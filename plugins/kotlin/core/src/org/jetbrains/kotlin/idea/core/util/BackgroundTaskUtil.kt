// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.*

data class BackgroundTask(val parent: Disposable, val indicator: ProgressIndicator, val future:CompletableFuture<*>) {

    fun cancelAndAwaitCompletion() {
        cancel()
        awaitCompletion()
    }

    fun cancel() {
        indicator.cancel()
    }

    fun awaitCompletion() {
        while(!future.isDone && !Disposer.isDisposed(parent)) {
            try {
                if (future.get(1, TimeUnit.SECONDS) != null) {
                    break
                }
            } catch (e: ExecutionException) {
                if (e.cause is ControlFlowException) {
                    break
                }
                throw e
            } catch (e: Exception) {
                if (e is ControlFlowException) {
                    break
                }
                throw e
            }
        }
    }
}

class BackgroundTaskUtil {
    companion object {
        private val logger = Logger.getInstance(BackgroundTaskUtil::class.java)

        fun executeOnPooledThread(parent: Disposable, runnable: Runnable): BackgroundTask =
            executeOnPooledThread(AppExecutorUtil.getAppExecutorService(), parent, runnable)

        fun executeOnPooledThread(executor: ExecutorService, parent: Disposable, runnable: Runnable): BackgroundTask {
            val indicator: ProgressIndicator = EmptyProgressIndicator()
            indicator.start()

            val future: CompletableFuture<*> = CompletableFuture.runAsync(
                { ProgressManager.getInstance().runProcess(runnable, indicator) },
                executor
            )

            val disposable = Disposable {
                if (indicator.isRunning) indicator.cancel()
                try {
                    future[1, TimeUnit.SECONDS]
                } catch (e: ExecutionException) {
                    if (e.cause is ProcessCanceledException) {
                        // ignore: expected cancellation
                    } else {
                        logger.error(e)
                    }
                } catch (e: InterruptedException) {
                    logger.debug("Couldn't await background process on disposal: $runnable")
                } catch (e: TimeoutException) {
                    logger.debug("Couldn't await background process on disposal: $runnable")
                }
            }

            val registeredIfParentNotDisposed = run {
                if (parent is ComponentManager && parent.isDisposed) {
                    false
                } else Disposer.tryRegister(parent, disposable)
            }

            if (registeredIfParentNotDisposed) {
                future.whenComplete { _: Any?, _: Throwable? -> Disposer.dispose(disposable) }
            } else {
                indicator.cancel()
            }

            return BackgroundTask(parent, indicator, future)
        }
    }
}