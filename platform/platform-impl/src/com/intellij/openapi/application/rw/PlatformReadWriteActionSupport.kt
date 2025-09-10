// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ReadResult
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl
import com.intellij.openapi.application.impl.InternalThreading
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ObjectUtils
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.writeText
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal class PlatformReadWriteActionSupport : ReadWriteActionSupport {

  private val retryMarker: Any = ObjectUtils.sentinel("rw action")

  init {
    // init the write action counter listener
    ApplicationManager.getApplication().service<AsyncExecutionService>()
  }

  override fun smartModeConstraint(project: Project): ReadConstraint {
    check(!LightEdit.owns(project)) {
      "ReadConstraint.inSmartMode() can't be used in LightEdit mode, check that LightEdit.owns(project)==false before calling"
    }
    return SmartModeReadConstraint(project)
  }

  override fun committedDocumentsConstraint(project: Project): ReadConstraint {
    return CommittedDocumentsConstraint(project)
  }

  override suspend fun <X> executeReadAction(
    constraints: List<ReadConstraint>,
    undispatched: Boolean,
    blocking: Boolean,
    action: () -> X,
  ): X {
    return InternalReadAction(constraints, undispatched, blocking, action).runReadAction()
  }

  override fun <X, E : Throwable> computeCancellable(action: ThrowableComputable<X, E>): X {
    return cancellableReadAction {
      action.compute()
    }
  }

  override suspend fun <X> executeReadAndWriteAction(
    constraints: Array<out ReadConstraint>,
    runWriteActionOnEdt: Boolean,
    undispatched: Boolean,
    action: ReadAndWriteScope.() -> ReadResult<X>,
  ): X {
    while (true) {
      val (readResult: ReadResult<X>, stamp: Long) = executeReadAction(constraints.toList(), undispatched = undispatched, blocking = false) {
        Pair(ReadResult.Companion.action(), AsyncExecutionServiceImpl.getWriteActionCounter())
      }
      when (readResult) {
        is ReadResult.Value -> {
          return readResult.value
        }
        is ReadResult.WriteAction -> {
          val lock = application.threadingSupport
          val writeResult = if (runWriteActionOnEdt || lock == null) {
            executeWriteActionOnEdt(stamp, readResult.action)
          }
          else {
            executeWriteActionOnBackgroundWithAtomicCheck(lock, stamp, undispatched, readResult.action)
          }
          if (writeResult !== retryMarker) {
            @Suppress("UNCHECKED_CAST")
            return writeResult as X
          }
        }
      }
    }
  }

  private suspend fun <T> executeWriteActionOnEdt(originalStamp: Long, action: () -> T): /*T or retryMarker */ Any? {
    return withContext(Dispatchers.EDT) {
      val writeStamp = AsyncExecutionServiceImpl.getWriteActionCounter()
      if (originalStamp == writeStamp) {
        application.runWriteAction(Computable {
          action()
        })
      }
      else {
        retryMarker
      }
    }
  }

  private suspend fun <T> executeWriteActionOnBackgroundWithAtomicCheck(lock: ThreadingSupport, originalStamp: Long, undispatched: Boolean, action: () -> T): /*T or retryMarker */ Any? {
    val dispatcher = if (undispatched) EmptyCoroutineContext else Dispatchers.Default
    val ref = withContext(dispatcher + InternalThreading.RunInBackgroundWriteActionMarker) {
      lock.runWriteActionWithCheckInWriteIntent(
        {
          val writeStamp = AsyncExecutionServiceImpl.getWriteActionCounter()
          return@runWriteActionWithCheckInWriteIntent originalStamp == writeStamp
        }, {
          // ref because we want to handle nullable T
          // if only we had union types in Kotlin...
          Ref(action())
        })
    }
    return if (ref == null) retryMarker else ref.get()
  }

  override suspend fun <T> runWriteAction(action: () -> T): T {
    val context = if (useBackgroundWriteAction) {
      Dispatchers.Default + InternalThreading.RunInBackgroundWriteActionMarker
    }
    else {
      Dispatchers.EDT
    }

    return withContext(context) {
      val dumpJob = if (useBackgroundWriteAction) launch {
        delay(10.seconds)
        val dump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false)
        val dumpDir = PathManager.getLogDir().resolve("bg-wa")
        val file = dumpDir.resolve("thread-dump-${Random.nextInt().absoluteValue}.txt")
        try {
          Files.createDirectories(dumpDir)
          Files.createFile(file)
          file.writeText(dump.rawDump)
          logger<ApplicationManager>().warn(
            """Cannot execute background write action in 10 seconds. Thread dump is stored in ${file.toUri()}""")
        }
        catch (_: IOException) {
          logger<ApplicationManager>().warn(
            """Cannot execute background write action in 10 seconds.
Thread dump:
${dump.rawDump}""")
        }

      }
      else null
      val application = ApplicationManager.getApplication()
      val lock = application.threadingSupport
      try {
        if (useBackgroundWriteAction && useTrueSuspensionForWriteAction && lock != null) {
          InternalThreading.incrementBackgroundWriteActionCount()
          try {
            lock.runWriteAction(action)
          }
          finally {
            InternalThreading.decrementBackgroundWriteActionCount()
          }
        }
        else {
          @Suppress("ForbiddenInSuspectContextMethod")
          application.runWriteAction(ThrowableComputable(action))
        }
      }
      finally {
        dumpJob?.cancel()
      }
    }
  }
}
