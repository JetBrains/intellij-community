// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File

abstract class AbstractKotlinModificationEventTest<TRACKER : ModificationEventTracker> : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override fun isFirPlugin(): Boolean = true

    protected fun createProjectLibrary(name: String): Library = ConfigLibraryUtil.addProjectLibraryWithClassesRoot(myProject, name)

    protected fun createScript(name: String, text: String = ""): KtFile =
        createKtFileUnderNewContentRoot(FileWithText("$name.kts", text))

    protected fun createNotUnderContentRootFile(name: String, text: String = ""): KtFile =
        // While the not-under-content-root module is named as it is, it is still decidedly under the project's content root, just not a
        // part of any other kind of `KtModule`.
        createKtFileUnderNewContentRoot(FileWithText("$name.kt", text))
}

abstract class ModificationEventTracker(
    private val project: Project,
    private val eventKind: String,
) : Disposable {
    protected class ReceivedEvent(val isRemoval: Boolean)

    protected val receivedEvents: MutableList<ReceivedEvent> = mutableListOf()

    fun initialize(testRootDisposable: Disposable) {
        Disposer.register(testRootDisposable, this)

        val busConnection = project.analysisMessageBus.connect(this)
        configureSubscriptions(busConnection)
    }

    protected abstract fun configureSubscriptions(busConnection: MessageBusConnection)

    override fun dispose() { }

    fun assertNotModified(label: String) {
        Assert.assertTrue(
            "${eventKind.replaceFirstChar { it.uppercaseChar() }} events for '$label' should not have been published, but ${receivedEvents.size} events were received.",
            receivedEvents.isEmpty(),
        )
    }

    fun assertModifiedOnce(label: String, shouldBeRemoval: Boolean = false) {
        assertModified(label, expectedEventCount = 1, shouldBeRemoval)
    }

    fun assertModified(label: String, expectedEventCount: Int, shouldBeRemoval: Boolean = false) {
        val eventCountString = if (expectedEventCount == 1) "A single" else expectedEventCount.toString()
        val eventOrEvents = if (expectedEventCount == 1) "event" else "events"
        Assert.assertTrue(
            "$eventCountString $eventKind $eventOrEvents for '$label' should have been published, but ${receivedEvents.size} events were received.",
            receivedEvents.size == expectedEventCount,
        )

        val shouldOrShouldNotBeRemoval = if (shouldBeRemoval) "should" else "should not"
        receivedEvents.forEachIndexed { index, receivedEvent ->
            val indexInfo = if (expectedEventCount > 0) " (event index: $index)" else ""
            Assert.assertTrue(
                "The $eventKind event for '$label' $shouldOrShouldNotBeRemoval be a removal event$indexInfo.",
                receivedEvent.isRemoval == shouldBeRemoval,
            )
        }
    }
}
