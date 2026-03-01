// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiDocumentManager.getInstance
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.api.platform.modification.isGlobalLevel
import org.jetbrains.kotlin.analysis.api.platform.modification.isModuleLevel
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File

abstract class AbstractKotlinModificationEventTest : AbstractMultiModuleTest() {
    protected open val defaultAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet()

    override fun getTestDataDirectory(): File = error("Should not be called")

    final override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    protected fun createProjectLibrary(name: String): Library = ConfigLibraryUtil.addProjectLibraryWithClassesRoot(myProject, name)

    protected fun createScript(name: String, text: String = ""): KtFile =
        createKtFileUnderNewContentRoot(FileWithText("$name.kts", text))

    protected fun createNotUnderContentRootFile(name: String, text: String = ""): KtFile =
        // While the not-under-content-root module is named as it is, it is still decidedly under the project's content root, just not a
        // part of any other kind of `KaModule`.
        createKtFileUnderNewContentRoot(FileWithText("$name.kt", text))

    /**
     * Creates and initializes a tracker to track global modification events. The tracker will be disposed with the test root disposable and
     * does not need to be disposed manually.
     */
    protected fun createGlobalTracker(
        label: String,
        expectedEventKind: KotlinModificationEventKind,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModificationEventTracker {
        require(expectedEventKind.isGlobalLevel)

        return ModificationEventTracker(
            project,
            label,
            expectedEventKind,
            additionalAllowedEventKinds + defaultAllowedEventKinds,
            testRootDisposable,
        )
    }

    /**
     * Creates and initializes a tracker to track module modification events. The tracker will be disposed with the test root disposable and
     * does not need to be disposed manually.
     */
    protected fun createModuleTracker(
        module: KaModule,
        label: String,
        expectedEventKind: KotlinModificationEventKind,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModuleModificationEventTracker {
        require(expectedEventKind.isModuleLevel)

        return ModuleModificationEventTracker(
            module,
            label,
            expectedEventKind,
            additionalAllowedEventKinds + defaultAllowedEventKinds,
            testRootDisposable,
        )
    }

    protected fun KtFile.modify(textAfterModification: String, targetOffset: Int? = null, edit: () -> Unit) {
        targetOffset?.let(editor.caretModel::moveToOffset)
        edit()
        getInstance(this.project).commitAllDocuments()
        Assert.assertEquals(textAfterModification, this.text)
    }
}
