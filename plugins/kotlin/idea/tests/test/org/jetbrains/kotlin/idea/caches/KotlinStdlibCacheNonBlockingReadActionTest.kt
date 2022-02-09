// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.ExceptionUtil
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.KotlinStdlibCache
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.getIdeaModelInfosCache
import org.jetbrains.kotlin.idea.core.util.runInReadActionWithWriteActionPriority
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.io.File
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

class KotlinStdlibCacheNonBlockingReadActionTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File {
        return IDEA_TEST_DATA_DIR.resolve("stdlibCache")
    }

    override fun setUp() {
        super.setUp()
        setupMppProjectFromDirStructure(getTestDataDirectory())
    }

    fun testStdlibCacheReadActionIsCancellableByWriteActions() {
        val secondaryThread = thread(start = true, name = "Cache-using thread") {
            runInReadActionWithWriteActionPriority {
                searchForStdlibUntilInterrupted()
            }
        }

        runWriteActionsLoop()

        secondaryThread.join()

        assertEmpty(exceptions.joinToString("\n\n") { exception ->
            ExceptionUtil.getThrowableText(exception)
        }, exceptions)
    }

    @Volatile
    private var cancelled = false
    private val exceptions = ArrayList<Throwable>()
    private val barrier = CyclicBarrier(2)

    private val stdlibCache: KotlinStdlibCache
        get() = project.getService(KotlinStdlibCache::class.java)

    private val moduleInfo: IdeaModuleInfo
        get() {
            return getIdeaModelInfosCache(project).forPlatform(JvmPlatforms.jvm17).filterIsInstance<ModuleSourceInfo>().singleOrNull()
                ?: throw AssertionError("Expected to find a single JVM module in the project")
        }

    // Search for Kotlin stdlib in module dependencies until cancelled
    // Running to the end means the read action used by the cache is non-cancellable and blocks pending write actions
    private fun searchForStdlibUntilInterrupted() {
        try {
            barrier.await()
            stdlibCache.findStdlibInModuleDependencies(moduleInfo)
        } catch (e: Throwable) {
            if (e !is ProcessCanceledException) {
                exceptions.add(e)
            }
            return
        } finally {
            cancelled = true
        }

        AssertionError("Expected cancellation didn't happen, the task has finished").also { assertionError ->
            exceptions.add(assertionError)
            throw assertionError
        }
    }

    // launch write actions until non-UI thread stops on cancellation point (or finishes in case of unwanted blocking read)
    private fun runWriteActionsLoop() {
        try {
            barrier.await()
            while (!cancelled) {
                runWriteAction {
                }
            }
        } catch (e: Exception) {
            exceptions.add(e)
        }
    }
}
