// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.diagnostic.AsyncLogKt;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.IdeaLogRecord;
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.UnhandledException;
import com.intellij.openapi.diagnostic.UnhandledExceptionKind;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IdeaLoggerTest extends BareTestFixtureTestCase {
  @Before
  public void clearErrorsOccurred() {
    IdeaLogger.ourErrorsOccurred = null;
  }

  @After
  public void dropCaches() {
    IdeaLogger.dropFrequentExceptionsCaches();
    IdeaLogger.ourErrorsOccurred = null;
  }

  private static void log(Logger logger, Level priority, String message, Throwable t) {
    if (priority == Level.WARNING) {
      logger.warn(message, t);
    }
    else if (priority == Level.SEVERE) {
      logger.error(message, t);
    }
  }

  @Test
  public void testExceptionDoesNotGetLoggedTwice() {
    for (Level level : new Level[]{Level.SEVERE, Level.WARNING}) {
      Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
      Throwable t = new Throwable(level.toString());
      Logger logger = getDelegate(diags);

      log(logger, level, null, t);
      log(logger, level, null, t);
      AsyncLogKt.awaitLogQueueProcessed();

      assertEquals("Too many " + level + " posted: " + diags, 1, diags.size());
      assertEquals("Too many " + level + " posted: " + diags, 1, diags.get(level).size());

      Pair<String, Throwable> first = diags.get(level).get(0);
      assertSame("Second error doesn't contain throwable: " + first, t, first.second);
    }
  }

  /// A wrapper has only a few construction sites, so `IdeaLogger` must count the real cause. See IJPL-254578.
  @Test
  public void testUnhandledExceptionsWithDifferentCausesAreBothLogged() {
    Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
    Logger logger = getDelegate(diags);

    // One message, two stacks. Only the stack of the cause tells the two apart.
    // The loop keeps one construction site for the wrapper, as `processUnhandledException` has in production.
    for (Throwable cause : List.of(causeFromFirstFrame(), causeFromSecondFrame())) {
      logger.error(null, new UnhandledException(cause, true));
    }
    AsyncLogKt.awaitLogQueueProcessed();

    assertEquals("Both unhandled exceptions must be logged: " + diags, 2, diags.get(Level.SEVERE).size());
  }

  @Test
  public void testTheSameUnhandledExceptionIsLoggedOnce() {
    Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
    Throwable cause = new Throwable("boom");
    Logger logger = getDelegate(diags);

    logger.error(null, new UnhandledException(cause, true));
    logger.error(null, new UnhandledException(cause, true));
    AsyncLogKt.awaitLogQueueProcessed();

    assertEquals("A repeated exception must be muted: " + diags, 1, diags.get(Level.SEVERE).size());
  }

  @Test
  public void testUnhandledExceptionIsWrittenAsItsRealCause() {
    Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
    Throwable cause = new Throwable("boom");
    Logger logger = getDelegate(diags);

    logger.error(null, new UnhandledException(cause, true));
    AsyncLogKt.awaitLogQueueProcessed();

    Throwable written = diags.get(Level.SEVERE).getFirst().second;
    String text = IdeaLogRecordFormatter.formatThrowable(written);
    assertFalse("The log text must not name the wrapper: " + text, text.contains(UnhandledException.class.getName()));
    assertEquals(IdeaLogRecordFormatter.formatThrowable(cause), text);
  }

  /// A test framework throws `ourErrorsOccurred`, so a developer must not read the wrapper there. See IJPL-254578.
  @Test
  public void testErrorsOccurredNamesTheRealCause() {
    Throwable cause = new Throwable("boom");
    Logger logger = getDelegate(new ConcurrentHashMap<>());

    logger.error(null, new UnhandledException(cause, true));

    assertSame("`ourErrorsOccurred` must name the real cause", cause, IdeaLogger.ourErrorsOccurred.getCause());
  }

  /// A wrapper can hold a control-flow exception, and the caller must get that exception back.
  @Test
  public void testUnhandledControlFlowExceptionIsRethrownAsItsRealCause() {
    ProcessCanceledException cause = new ProcessCanceledException();
    Logger logger = getDelegate(new ConcurrentHashMap<>());

    Throwable thrown = assertThrows(ProcessCanceledException.class, () -> logger.error(null, new UnhandledException(cause, true)));

    assertSame("The caller must get the real cause, not the wrapper", cause, thrown);
  }

  /// `error` builds a new throwable for the attachments, and the kind must survive that. See IJPL-254578.
  @Test
  public void testAttachmentsKeepTheKind() {
    List<LogRecord> records = new CopyOnWriteArrayList<>();
    Logger logger = getDelegate(new ConcurrentHashMap<>(), records);

    logger.error("msg", new UnhandledException(new Throwable("attached"), true), new Attachment("a.txt", "hello"));
    AsyncLogKt.awaitLogQueueProcessed();

    LogRecord record = withThrowable(records);
    assertEquals("The kind must survive the attachments", UnhandledExceptionKind.INTERACTIVE,
                 ((IdeaLogRecord)record).getUnhandledExceptionKind());
    assertTrue("The attachments must survive too, but was: " + record.getThrown(),
               record.getThrown() instanceof ExceptionWithAttachments);
  }

  /// `error` with attachments counted the throwable, and then it delegated to the overload that counts again.
  /// The second count muted the call. One call must count once. See IJPL-254578.
  @Test
  public void testOneCallWithNoAttachmentIsLoggedOnce() {
    Map<Level, List<Pair<String, Throwable>>> diags = new ConcurrentHashMap<>();
    Throwable t = new Throwable("boom");
    Logger logger = getDelegate(diags);

    logger.error("msg", t, Attachment.EMPTY_ARRAY);
    AsyncLogKt.awaitLogQueueProcessed();

    assertEquals("One call must write one record: " + diags, 1, diags.getOrDefault(Level.SEVERE, List.of()).size());
  }

  /// The guard for every handler on the root logger. A handler reads the record, so the record must hold
  /// the real cause. Only the kind travels apart. See IJPL-254578 and `IdeaLogRecord`.
  @Test
  public void testTheRecordHoldsTheRealCauseAndTheKindApart() {
    List<LogRecord> records = new CopyOnWriteArrayList<>();
    Throwable cause = new Throwable("boom");
    Logger logger = getDelegate(new ConcurrentHashMap<>(), records);

    logger.error("msg", new UnhandledException(cause, true));
    AsyncLogKt.awaitLogQueueProcessed();

    LogRecord record = withThrowable(records);
    assertSame("A handler must get the real cause", cause, record.getThrown());
    assertTrue("The kind needs an `IdeaLogRecord`, but was: " + record.getClass(), record instanceof IdeaLogRecord);
    assertEquals(UnhandledExceptionKind.INTERACTIVE, ((IdeaLogRecord)record).getUnhandledExceptionKind());
    assertEquals("`Logger.log(LogRecord)` sets no name, so `JulLogger` must", "", record.getLoggerName());
  }

  @Test
  public void testTheRecordOfAHandledExceptionIsHandled() {
    List<LogRecord> records = new CopyOnWriteArrayList<>();
    Throwable t = new Throwable("boom");
    Logger logger = getDelegate(new ConcurrentHashMap<>(), records);

    logger.error("msg", t);
    AsyncLogKt.awaitLogQueueProcessed();

    LogRecord record = withThrowable(records);
    assertSame(t, record.getThrown());
    if (record instanceof IdeaLogRecord ideaLogRecord) {
      assertEquals("An exception that was handled is handled",
                   UnhandledExceptionKind.HANDLED,
                   ideaLogRecord.getUnhandledExceptionKind());
    }
  }

  private static LogRecord withThrowable(@NotNull List<LogRecord> records) {
    List<LogRecord> withThrowable = new ArrayList<>();
    for (LogRecord record : records) {
      if (record.getThrown() != null) withThrowable.add(record);
    }
    assertEquals("Exactly one record must hold a throwable: " + withThrowable, 1, withThrowable.size());
    return withThrowable.getFirst();
  }

  private static Throwable causeFromFirstFrame() {
    return new Throwable("boom");
  }

  private static Throwable causeFromSecondFrame() {
    return new Throwable("boom");
  }

  @NotNull
  private static Logger getDelegate(@NotNull Map<? super Level, List<Pair<String, Throwable>>> diags) {
    return getDelegate(diags, new CopyOnWriteArrayList<>());
  }

  /// Captures `LogRecord`, because `IdeaLogger` uses both `log(Level, String, Throwable)` and `log(LogRecord)`.
  /// Every `log` overload of the JDK ends in `log(LogRecord)`, so one override sees them all.
  @NotNull
  private static Logger getDelegate(@NotNull Map<? super Level, List<Pair<String, Throwable>>> diags,
                                    @NotNull List<LogRecord> records) {
    java.util.logging.Logger julLogger = new java.util.logging.Logger("", null) {
      @Override
      public void log(LogRecord record) {
        records.add(record);
        if (record.getThrown() != null) {
          diags.computeIfAbsent(record.getLevel(), _ -> new ArrayList<>())
            .add(Pair.create(String.valueOf(record.getMessage()), record.getThrown()));
        }
      }
    };
    julLogger.setLevel(Level.INFO);
    IdeaLogger logger = new IdeaLogger(julLogger);
    assertTrue(IdeaLogger.isMutingFrequentExceptionsEnabled());
    return logger;
  }
}
