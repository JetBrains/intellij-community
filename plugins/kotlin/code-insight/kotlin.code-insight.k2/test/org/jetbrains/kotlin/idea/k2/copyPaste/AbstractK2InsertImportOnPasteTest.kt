// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.copyPaste

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import org.jetbrains.kotlin.idea.codeInsight.AbstractInsertImportOnPasteTest
import org.jetbrains.kotlin.idea.codeInsight.copyPaste.KotlinCopyPasteCoroutineScopeService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.trimTrailingWhitespacesAndRemoveRedundantEmptyLinesAtTheEnd
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class AbstractK2InsertImportOnPasteTest : AbstractInsertImportOnPasteTest() {
    override fun checkResults(expectedResultFile: File, resultFile: KtFile) {
        assertEquals(
            expectedResultFile.getTextWithoutErrorDirectives().trimTrailingWhitespacesAndRemoveRedundantEmptyLinesAtTheEnd(),
            resultFile.text.trimTrailingWhitespacesAndRemoveRedundantEmptyLinesAtTheEnd()
        )
    }

    private fun File.getTextWithoutErrorDirectives(): String {
        val directives = setOf("// ERROR:")

        return readLines().filterNot { line -> directives.any { line.startsWith(it) } }.joinToString("\n")
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun waitForAsyncPasteCompletion() {
        val coroutineScope = KotlinCopyPasteCoroutineScopeService.Companion.getCoroutineScope(project)
        val future = GlobalScope.future { coroutineScope.coroutineContext.job.children.toList().joinAll() }

        for (i in 0 until 60_000) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            try {
                future.get(1, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                continue
            }
        }
    }
}