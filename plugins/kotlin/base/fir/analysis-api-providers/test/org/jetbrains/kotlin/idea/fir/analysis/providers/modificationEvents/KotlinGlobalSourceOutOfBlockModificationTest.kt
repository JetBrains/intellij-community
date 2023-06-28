// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.kotlin.analysis.providers.topics.KotlinGlobalSourceOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.util.sourceRoots

class KotlinGlobalSourceOutOfBlockModificationTest : AbstractKotlinGlobalModificationEventTest<GlobalSourceOutOfBlockModificationEventTracker>() {
    override fun constructTracker() = GlobalSourceOutOfBlockModificationEventTracker(myProject)

    fun `test that global source out-of-block modification occurs after a file is added to a module content root`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val tracker = createTracker()

        runWriteAction {
            moduleA.sourceRoots.first().createChildData(/* requestor = */ null, "file.kt")
        }

        tracker.assertModifiedOnce("the project after a file is added to the content root of module A")

        disposeTrackers(tracker)
    }

    fun `test that global source out-of-block modification occurs after a file is moved to another module content root`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() {}")
            )
        }
        val moduleB = createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val tracker = createTracker()

        val file = moduleA.sourceRoots.first().findChild("main.kt")!!
        val destination = moduleB.sourceRoots.first()
        move(file, destination)

        tracker.assertModifiedOnce("the project after a file from module A is moved to the content root of module B")

        disposeTrackers(tracker)
    }
}

class GlobalSourceOutOfBlockModificationEventTracker(project: Project) : GlobalModificationEventTracker(
    project,
    eventKind = "global source out-of-block modification",
) {
    override fun configureSubscriptions(busConnection: MessageBusConnection) {
        busConnection.subscribe(
            KotlinTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION,
            KotlinGlobalSourceOutOfBlockModificationListener {
                 receivedEvents.add(ReceivedEvent(isRemoval = false))
            },
        )
    }
}
