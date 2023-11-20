// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PsiTestUtil
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
    }

    fun `test that global source out-of-block modification occurs after moving a script file to a non-source module content root`() {
        val scriptA = createScript("a")
        val moduleB = createModuleInTmpDir("b")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleB, destination)

        val tracker = createTracker()

        move(scriptA.virtualFile, destination)

        tracker.assertModifiedOnce("the project after a script file is moved to a non-source content root of module B")
    }

    fun `test that global source out-of-block modification occurs after moving a script file outside the project content root`() {
        val scriptA = createScript("a")
        val destination = getVirtualFile(createTempDirectory())

        val tracker = createTracker()

        move(scriptA.virtualFile, destination)

        tracker.assertModifiedOnce("the project after a script file is moved outside the project content root")
    }

    // TODO (marco): This test can be enabled once IDEA-324516 is fixed.
    //fun `test that global source out-of-block modification occurs after deleting a script file`() {
    //    val scriptA = createScript("a")
    //
    //    val tracker = createTracker()
    //
    //    delete(scriptA.virtualFile)
    //
    //    tracker.assertModified("the project after a script file is deleted", expectedEventCount = 2)
    //}

    fun `test that global source out-of-block modification occurs after moving a not-under-content-root file to a non-source module content root`() {
        val fileA = createNotUnderContentRootFile("a")
        val moduleB = createModuleInTmpDir("b")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleB, destination)

        val tracker = createTracker()

        move(fileA.virtualFile, destination)

        tracker.assertModifiedOnce("the project after a not-under-content-root file is moved to a non-source content root of module B")
    }

    fun `test that global source out-of-block modification occurs after moving a not-under-content-root file outside the project content root`() {
        val fileA = createNotUnderContentRootFile("a")
        val destination = getVirtualFile(createTempDirectory())

        val tracker = createTracker()

        // Note that the "not-under-content-root" file is under the content root of the project, and so moving it outside the content root
        // of the project does have an effect.
        move(fileA.virtualFile, destination)

        tracker.assertModifiedOnce("the project after a not-under-content-root file is moved outside the project content root")
    }

    fun `test that global source out-of-block modification occurs after deleting a not-under-content-root file`() {
        val fileA = createNotUnderContentRootFile("a")

        val tracker = createTracker()

        delete(fileA.virtualFile)

        tracker.assertModified("the project after a not-under-content-root file is deleted", expectedEventCount = 2)
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
