// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.util.SystemProperties.getIntProperty;

/**
 * A class intended to overcome interruptibility of {@link FileChannel} by repeating passed operation
 * until it will be successfully applied.
 * <p>
 * If underlying {@link FileChannel} throws {@link ClosedByInterruptException} -> we close and reopen the
 * channel, and try the operation again.
 * <p>
 * Basically, this class tries to provide something similar to Atomicity (Transaction) -- i.e. ensure
 * {@link FileChannelIdempotentOperation} is either applied successfully, or not at all (if Retryer is
 * closed before the operation starts) -- but operation can't be 'partially applied'.
 * This is important because 'partially applied' operation usually means that either on-disk (in case of
 * write-ops) or in-memory (in case of read-ops) data structure is left in inconsistent (=corrupted) state,
 * and we want to prevent that. So we repeatedly re-open underlying FileChannel, and apply operation
 * on the top of it, until either the operation succeeds, or until heat death of the universe -- whatever
 * comes first.
 * <p>
 * WARNING: class API doesn't prevent incorrect usage, so needs caution: to be safely repeatable
 * {@link FileChannelIdempotentOperation} implementation must be free of side effects (except for
 * FileChannel modifications), and must not rely on FileChannel position from previous operations
 * (because position will be lost during channel re-opening). For simpler API consider use of
 * {@link ResilientFileChannel}. {@link ResilientFileChannel} could be seen as a counterpart of this
 * class: this class implements kind-of-atomicity for logical units of work ({@link FileChannelIdempotentOperation}),
 * while {@link ResilientFileChannel} implements same kind of atomicity for elementary operations,
 * like read & write. It is easier to use, but has slightly more overhead.
 * <p/>
 * TODO RC: current implementation catches-and-retries not only {@link ClosedByInterruptException},
 * but any {@link ClosedChannelException}. This (likely) serves same goal of keeping data structures in a
 * consistent state -- if Channel is closed from another thread in the middle of an operation, this
 * also most probably leads to corrupted data structure. But this violates description above, and
 * the class name itself is misleading then -- class actually does a more general task than claims. If
 * we insist to keep 'retry on anything' then class name and description should be adjusted accordingly.
 * Also we could use already existing libs (e.g. <a href="https://github.com/failsafe-lib/failsafe">FailSafe</a>)
 * for that.
 */
@ApiStatus.Internal
public final class FileChannelInterruptsRetryer implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(FileChannelInterruptsRetryer.class);

  /** If a single IO operation still not succeeds after that many retries -- fail */
  @VisibleForTesting
  @ApiStatus.Internal
  public static final int MAX_RETRIES = getIntProperty("idea.vfs.FileChannelInterruptsRetryer.MAX_RETRIES", 64);

  /**
   * If value > 0: add stacktrace to each Nth in a row retry warning log message
   * If value <=0: never add stacktrace to a retry warning log message
   */
  private static final int LOG_STACKTRACE_IF_RETRY_CHAIN_LONGER = getIntProperty("idea.vfs.LOG_STACKTRACE_IF_RETRY_CHAIN_LONGER", 32);

  private final @NotNull Lock openCloseLock = new ReentrantLock();
  private final @NotNull Path path;
  private final Set<? extends @NotNull OpenOption> openOptions;

  /** null if retryer has been closed */
  private volatile FileChannel channel;

  /**
   * Total number of retries across all instances of this class. Only retries count -- a first
   * successful attempt doesn't.
   */
  private static final AtomicLong totalRetriedAttempts = new AtomicLong();

  public static long totalRetriedAttempts(){
    return totalRetriedAttempts.get();
  }


  public FileChannelInterruptsRetryer(final @NotNull Path path,
                                      final Set<? extends @NotNull OpenOption> openOptions) throws IOException {
    this.path = path;
    this.openOptions = openOptions;
    reopenChannel();
  }

  public <T> T retryIfInterrupted(final @NotNull FileChannelIdempotentOperation<T> operation) throws IOException {
    boolean interruptedStatusWasCleared = false;
    try {
      for (int attempt = 0; ; attempt++) {
        final FileChannel channelLocalCopy = channel;
        if (channelLocalCopy == null && attempt == 0) {
          //we haven't tried yet -> just reject
          throw new ClosedChannelException();
        }
        try {
          if (channelLocalCopy == null && attempt >= 0) {
            //we have tried already, and failed, so now we can't just reject, since previous unsuccessful
            // attempts may corrupt the data -> throw CCException (which will be caught and re-tried)
            throw new ClosedChannelException();
          }
          return operation.execute(channelLocalCopy);
        }
        catch (ClosedChannelException e) {
          totalRetriedAttempts.incrementAndGet();

          if (attempt >= MAX_RETRIES) {
            IOException ioe = new IOException(
              "Channel[" + path + "][@" + System.identityHashCode(channelLocalCopy) + "] " +
              "is interrupted/closed in the middle of operation " + MAX_RETRIES + " times in the row: surrender");
            ioe.addSuppressed(e);
            throw ioe;
          }
          //TODO RC: this catches _all_ close causes, not only thread interruptions!
          //         (for the latter only ClosedByInterruptException should be caught)
          //         ...Actually, catching all ClosedXXXExceptions make sense for the primary
          //         purpose of the class -- avoid corrupted IO-ops by making them 'atomic'
          //         (kind-of) i.e. all-or-nothing. From the PoV of keeping data structures
          //         in a consistent state -- async .close() is the same kind of danger as
          //         Thread.interrupt(): both could interrupt IO-op mid-flight giving corrupted
          //         result or leaving data structure in a corrupted state. Hence it is pretty
          //         reasonable to work around both issues same way.
          //         There are 2 issues here, though:
          //         1) That behavior is inconsistent with that class javadocs (and even class
          //         name) promises -- docs promise the class deals with Thread.interrupt(),
          //         while really it does more than that, which could come as surprise for
          //         somebody naive enough to trust the docs.
          //         2) Class currently handles async .close() in an inconsistent way: inconsistent
          //         with how .interrupt() is handled, and internally-inconsistent. I.e. async .close()
          //         could make Retryer 'closed' forever -- or could be silently ignored, depending
          //         on exact timing of the calls.
          //         This is not how .interrupt() is handled: interrupt()-ed IO-op is repeated until
          //         succeed, but Thread.interrupted status is remembered, and restored after op is
          //         succeed -- so interrupt() is not 'swallowed' silently, it is just 'postponed'
          //         until op currently in-flight finishes.
          //         But for async .close() it is different: if 'closed' status observed _before_
          //         IOps started -> ChannelClosedException is thrown, and Retryer remains closed
          //         forever, all following IOps will fail with the same exception. But if closed
          //         status observed in the middle of an operation -> channel is reopened, and
          //         closed status just silently disappears. This is hardly the 'least surprise'
          //         behavior, so must be fixed: either we handle async .close() same ways as
          //         .interrupt() -- i.e. postpone actual closing until current IOps finishes,
          //         but restore the 'closed' status afterwards -- or we explicitly state in the
          //         docs that 'closed' state is reversible, and remove 'if (!isOpen())' branch
          //         from the method start.
          //         (See @Ignored test ResilientFileChannel_MultiThreaded_Test.onceClosed_FileChannelInterruptsRetryer_RemainsClosedForever
          //         which fails now)


          boolean logStackTrace = LOG_STACKTRACE_IF_RETRY_CHAIN_LONGER > 0
                                  && (attempt % LOG_STACKTRACE_IF_RETRY_CHAIN_LONGER == (LOG_STACKTRACE_IF_RETRY_CHAIN_LONGER - 1));
          if (logStackTrace) {
            LOG.warn("Channel[" + path + "][@" + System.identityHashCode(channelLocalCopy) + "] is closed during " + operation
                     + " " + LOG_STACKTRACE_IF_RETRY_CHAIN_LONGER + " times in a row -- suspicious, log stacktrace", e);
          }
          else {
            LOG.warn("Channel[" + path + "][@" + System.identityHashCode(channelLocalCopy) + "] is closed during " + operation
                     + " => trying to reopen it again. Reason: " + e);
          }
          if (Thread.currentThread().isInterrupted()) {
            Thread.interrupted();//must clear interrupted status, otherwise newly opened channel throws ClosedByInterrupt again
            interruptedStatusWasCleared = true;
          }
          //TODO RC: should we remember 'close' status also, and apply it after the loop?

          reopenChannel();
        }
      }
    }
    finally {
      if (interruptedStatusWasCleared) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public boolean isOpen() {
    return channel != null;
  }

  @Override
  public void close() throws IOException {
    ConcurrencyUtil.withLock(openCloseLock, this::tryClose);
  }

  private void reopenChannel() throws IOException {
    ConcurrencyUtil.withLock(openCloseLock, () -> {
      try {
        tryClose();
      }
      catch (IOException e) {//RC: Why swallow this exception?
        LOG.info("Can't close channel[" + path + "]: " + e.getMessage());
      }
      channel = FileChannel.open(path, openOptions);
    });
  }

  private void tryClose() throws IOException {
    try {
      FileChannel channel = this.channel;
      if (channel != null && channel.isOpen()) {
        channel.close();
      }
    }
    finally {
      channel = null;
    }
  }

  public interface FileChannelIdempotentOperation<T> {
    /**
     * Implementation must be idempotent: i.e. has no other side effects except for desirable changes
     * in fileChannel. Also, implementation shouldn't rely on FileChannel.position from previous calls:
     * if you want to read or write bytes at some offset -- either use absolute-positioned read-write
     * methods, or position fileChannel explicitly inside the lambda.
     */
    T execute(@NotNull FileChannel fileChannel) throws IOException;
  }
}
