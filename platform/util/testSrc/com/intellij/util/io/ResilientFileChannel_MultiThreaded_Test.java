// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.Ref;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.*;

/**
 * Test {@link ResilientFileChannel} behavior against async (from other thread) interrupts
 */
public class ResilientFileChannel_MultiThreaded_Test {
  @Rule
  public final TemporaryFolder tempDirectory = new TemporaryFolder();

  @Test
  public void readsFromResilientChannel_CompletedInFull_RegardlessOfThreadInterruptions() throws Throwable {
    final Path file = tempDirectory.newFile().toPath();
    final int enoughReadingChunks = 1000;
    final List<String> fileContentChunks = IntStream.range(0, enoughReadingChunks)
      .mapToObj(i -> "test string#%d".formatted(i))
      .toList();


    //Scenario: main thread writes to the file 1000 chunks of data
    //          reader thread reads 1000 chunks from file (+puts results in a list)
    //          main thread constantly interrupts reader thread
    //          main thread waits for reader thread to finish, and check all read results
    //          == fileContent written initially
    //Goal is to check that interruptions in the middle of .read() calls are harmless, i.e.
    //  RFileChannel successfully work around them, and assumptions made in its design
    //  are hold.

    Files.writeString(file, String.join("", fileContentChunks));

    try (ResilientFileChannel channel = new ResilientFileChannel(file, WRITE, READ)) {
      final List<String> stringsReadFromFile = new ArrayList<>();
      final Ref<Throwable> exceptionThrown = new Ref<>();
      final Thread readerThread = new Thread(() -> {
        for (int i = 0; i < enoughReadingChunks; i++) {
          final byte[] bytesOfChunkWritten = fileContentChunks.get(i).getBytes(UTF_8);
          final byte[] bytesToRead = new byte[bytesOfChunkWritten.length];
          final ByteBuffer buffer = ByteBuffer.wrap(bytesToRead);
          try {
            channel.read(buffer);
            final String string = new String(bytesToRead, UTF_8);
            stringsReadFromFile.add(string);

            //RC: Goal is to check interrupt in the middle of .read() calls does no harm.
            // Cleaning interruption status allows for more (not interrupted -> interrupted)
            // transitions to happen, making more chances for them to happen in the middle
            // of .read().
            Thread.interrupted();//clean interrupted status
          }
          catch (IOException e) {
            exceptionThrown.set(e);
            return;
          }
        }
      }, "channel reading thread");


      readerThread.start();
      constantlyInterruptThreadUntilItDies(readerThread);
      readerThread.join();


      if (!exceptionThrown.isNull()) {
        throw exceptionThrown.get();
      }

      assertEquals(
        "All read attempts should return the value written in a file before",
        stringsReadFromFile,
        fileContentChunks
      );
    }
  }

  @Test
  public void writesToResilientChannel_CompletedInFull_RegardlessOfThreadInterruptions() throws Throwable {
    final Path file = tempDirectory.newFile().toPath();
    final String singleLineToWrite = "test string\n";//end with EOL so could read with Files.readLines()
    final int enoughWrites = 1000;

    //Scenario: writer thread writes singleLineToWrite 1000 times
    //          main thread constantly interrupts writer thread
    //          main thread waits for writer thread to finish, reads all file content and check all
    //          all the lines are == singleLineToWrite
    //Goal is to check that interruptions in the middle of .write() calls are harmless, i.e.
    //  RFileChannel successfully work around them, keep position across channel reopening,
    //  and assumptions made in its design are hold.


    final byte[] lineToWriteAsBytes = singleLineToWrite.getBytes(UTF_8);

    try (ResilientFileChannel channel = new ResilientFileChannel(file, WRITE, READ)) {
      final Ref<Throwable> exceptionThrown = new Ref<>();
      final Thread writerThread = new Thread(() -> {
        for (int i = 0; i < enoughWrites; i++) {
          final ByteBuffer buffer = ByteBuffer.wrap(lineToWriteAsBytes);
          try {
            channel.write(buffer);
            //RC: Goal is to check interrupt in the middle of .write() calls does no harm.
            // Cleaning interruption status allows for more (not interrupted -> interrupted)
            // transitions to happen, making more chances for them to happen in the middle
            // of .read().
            Thread.interrupted();//clean interrupted status
          }
          catch (IOException e) {
            exceptionThrown.set(e);
            return;
          }
        }
      }, "channel writing thread");


      writerThread.start();
      constantlyInterruptThreadUntilItDies(writerThread);
      writerThread.join();


      if (!exceptionThrown.isNull()) {
        throw exceptionThrown.get();
      }

      final List<String> fileContentLines = Files.readAllLines(file);
      assertEquals(
        "Must be " + enoughWrites + " lines written",
        fileContentLines.size(),
        enoughWrites
      );
      assertEquals(
        "All lines must be the same",
        1,
        new HashSet<>(fileContentLines).size()
      );
      assertTrue(
        "All writes should write same value (mod EOL)",
        new HashSet<>(fileContentLines).contains(singleLineToWrite.trim())
      );
    }
  }

  @Test
  public void readsFromResilientChannel_CompletedInFull_OrNotCompletedAtAll_RegardlessOfChannelAsyncClose() throws Throwable {
    final Path file = tempDirectory.newFile().toPath();
    //make string long, so it takes time to read it -- this makes the interference window larger
    final String fileContentString = "testString|".repeat(100);
    final int enoughAttempts = 1000;
    final ExecutorService pool = ConcurrencyUtil.newSingleThreadExecutor("readers");
    try {

      //Scenario: main thread writes fileContentString to the file
      //          repeat 1000 times:
      //            - reader thread reads file content
      //            - main thread closes the RFChannel in parallel with reader thread
      //            - main thread waits for reader thread, and check the read result is not corrupted
      //              (i.e. the read result is either full fileContentString, or ChannelClosedException)
      //Goal is to check that RFChannel indeed keeps operations 'atomic' (all-or-nothing) even
      //          against async channel.close()


      final byte[] fileContentBytes = fileContentString.getBytes(UTF_8);
      Files.write(file, fileContentBytes);

      for (int attempt = 0; attempt < enoughAttempts; attempt++) {
        try (ResilientFileChannel channel = new ResilientFileChannel(file, WRITE, READ)) {
          final byte[] bytesToRead = new byte[fileContentBytes.length];
          final ByteBuffer buffer = ByteBuffer.wrap(bytesToRead);

          final Callable<String> readingTask = () -> {
            try {
              channel.read(buffer);
              return new String(bytesToRead, UTF_8);
            }
            catch (ClosedChannelException e) {
              //OK: channel closed, we read nothing
              return null;
            }
          };
          final Future<String> result = pool.submit(readingTask);

          Thread.yield();
          channel.close();
          Thread.yield();

          final String readResult = result.get();
          if (readResult != null) {
            assertEquals(
              "Successful read attempt must return the value written in a file before",
              fileContentString,
              readResult
            );
          }//else -> empty list is also a valid result, but should be pretty rare (=none of 1000 attempts succeeded)
        }
      }
    }
    finally {
      pool.shutdown();
    }
  }

  @Test
  public void onceClosed_ResilientFileChannel_RemainsClosedAfterwards() throws Throwable {
    final Path file = tempDirectory.newFile().toPath();
    //make string long, so it takes time to read it -- this makes the interference window larger
    final String fileContentString = "testString|".repeat(100);
    final int enoughAttempts = 1000;
    final ExecutorService pool = ConcurrencyUtil.newSingleThreadExecutor("closers");

    //Scenario: main thread writes fileContentString to the file
    //          repeat 1000 times:
    //            - main thread reads file content
    //            - closer thread closes the RFChannel in parallel with main thread
    //            - main thread waits for closer thread to finish, and check the channel is closed
    //Goal is to check that RFChannel.close() is irreversible: closed channel could delay its end-of-life
    //          to complete currently in-flight operations, but after that channel state is closed

    try {
      final byte[] fileContentBytes = fileContentString.getBytes(UTF_8);
      Files.write(file, fileContentBytes);

      for (int attempt = 0; attempt < enoughAttempts; attempt++) {
        final byte[] bytesToRead = new byte[fileContentBytes.length];
        final ByteBuffer buffer = ByteBuffer.wrap(bytesToRead);

        try (ResilientFileChannel channel = new ResilientFileChannel(file, WRITE, READ)) {
          final Callable<Void> closingTask = () -> {
            channel.close();
            return null;
          };
          final Future<Void> result = pool.submit(closingTask);
          Thread.yield();
          Thread.yield();

          try {
            channel.read(buffer);
            result.get();
          }
          catch (ClosedChannelException e) {
            //Also a valid result: .close() happens before .read() started
          }
          assertFalse(
            "Channel must be closed since parallel thread called .close() on it",
            channel.isOpen()
          );
        }
      }
    }
    finally {
      pool.shutdown();
    }
  }


  @Test
  @Ignore("Currently this is not true: .close() is inconsistently implemented")
  public void onceClosed_FileChannelInterruptsRetryer_RemainsClosedForever() throws Throwable {
    final Path file = tempDirectory.newFile().toPath();
    //make string long, so it takes time to read it -- this makes the interference window larger
    final String fileContentString = "testString|".repeat(100);
    final int enoughAttempts = 1000;
    final ExecutorService pool = ConcurrencyUtil.newSingleThreadExecutor("closers");

    //Scenario: main thread writes fileContentString to the file
    //          repeat N times:
    //            - main thread reads file content via Retryer
    //            - closer thread closes the Retryer in parallel with main thread
    //            - main thread waits for closer thread to finish, and check the Retryer is closed
    //Goal is to check that FCIRetryer.close() is irreversible: closed Retryer could delay its end-of-life
    //          to complete currently in-flight operations, but after that Retryer state is closed.
    //FIXME RC: currently this does not hold -- see comments in FileChannelInterruptsRetryer.retryIfInterrupted()

    try {
      final byte[] fileContentBytes = fileContentString.getBytes(UTF_8);
      Files.write(file, fileContentBytes);

      for (int attempt = 0; attempt < enoughAttempts; attempt++) {
        final byte[] bytesToRead = new byte[fileContentBytes.length];
        final ByteBuffer buffer = ByteBuffer.wrap(bytesToRead);

        try (FileChannelInterruptsRetryer retryer = new FileChannelInterruptsRetryer(file, Set.of(WRITE, READ))) {
          final Callable<Void> closingTask = () -> {
            retryer.close();
            return null;
          };
          final Future<Void> result = pool.submit(closingTask);
          Thread.yield();

          try {
            retryer.retryIfInterrupted(channel -> {
              channel.read(buffer);
              return null;
            });
            result.get();
          }
          catch (ClosedChannelException e) {
            //Also a valid result: .close() happens before .read() started
          }
          assertFalse(
            "[attempt #" + attempt + "]: Retryer must be closed since parallel thread called .close() on it",
            retryer.isOpen()
          );
        }
      }
    }
    finally {
      pool.shutdown();
    }
  }

  // ========= infrastructure: ====================================================================
  
  private static void constantlyInterruptThreadUntilItDies(@NotNull Thread victimThread) {
    int interruptsInARow = 0;
    while (victimThread.isAlive()) {
      if (!victimThread.isInterrupted()) {
        victimThread.interrupt();
        interruptsInARow++;
      }
      if (interruptsInARow >= FileChannelInterruptsRetryer.MAX_RETRIES / 2) {
        //We need to interrupt thread often enough to catch it mid-IO-operation, but not so often
        //  that we trigger 'too many retries' (current limit: 64 per op). Hence, the pause here:
        //  give current IOop a chance to complete and reset retry counter:
        LockSupport.parkNanos(100_000);
      }
    }
  }
}
