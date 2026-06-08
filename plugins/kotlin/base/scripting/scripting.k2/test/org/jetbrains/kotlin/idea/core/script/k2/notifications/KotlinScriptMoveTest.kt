// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.notifications

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile

class KotlinScriptMoveTest : KotlinLightCodeInsightFixtureTestCase() {

    

    fun `test empty script is moved by file processor`() {
        val sourceRoot = runWriteAction { myFixture.tempDirFixture.findOrCreateDir("src/main/kotlin") }
        PsiTestUtil.addSourceRoot(module, sourceRoot)
        val script = myFixture.addFileToProject("src/main/kotlin/empty.kts", "") as KtFile
        val target = runWriteAction { myFixture.tempDirFixture.findOrCreateDir("scripts") }
        val targetDirectory = PsiManager.getInstance(project).findDirectory(target) ?: error("Target directory must have PSI")

        moveKotlinScriptFile(project, script, targetDirectory)

        assertNotNull(target.findChild("empty.kts"))
        assertNull(sourceRoot.findFileByRelativePath("empty.kts"))
    }

    fun `test move targets start with recommended module root`() {
        val moduleRoot = addModuleContentRoot()
        val sourceRoot = addSourceRoot("module/src/main/kotlin")
        val resourcesRoot = addResourceRoot("module/src/main/resources")
        myFixture.addFileToProject("module/src/main/kotlin/script.kts", "")

        val targets = createKotlinScriptMoveTargets(project, sourceRoot.findChild("script.kts") ?: error("Script file must exist"))

        assertEquals(3, targets.size)
        val rootTarget = targets[0] as KotlinScriptMoveTarget.Directory
        assertTrue(rootTarget.isRecommended)
        assertEquals(moduleRoot, rootTarget.directory.virtualFile)
        assertFalse(rootTarget.text.contains(moduleRoot.path))

        val resourcesTarget = targets[1] as KotlinScriptMoveTarget.Directory
        assertFalse(resourcesTarget.isRecommended)
        assertEquals(resourcesRoot, resourcesTarget.directory.virtualFile)
        assertSame(KotlinScriptMoveTarget.SelectDirectory, targets[2])
    }

    fun `test move targets omit resources when module has no resources root`() {
        val moduleRoot = addModuleContentRoot()
        val sourceRoot = addSourceRoot("module/src/main/kotlin")
        myFixture.addFileToProject("module/src/main/kotlin/script.kts", "")

        val targets = createKotlinScriptMoveTargets(project, sourceRoot.findChild("script.kts") ?: error("Script file must exist"))

        assertEquals(2, targets.size)
        val rootTarget = targets[0] as KotlinScriptMoveTarget.Directory
        assertTrue(rootTarget.isRecommended)
        assertEquals(moduleRoot, rootTarget.directory.virtualFile)
        assertSame(KotlinScriptMoveTarget.SelectDirectory, targets[1])
    }

    fun `test script prefers test resources root`() {
        addModuleContentRoot()
        val sourceRoot = addSourceRoot("module/src/test/kotlin", isTestSource = true)
        addResourceRoot("module/src/main/resources")
        val testResourcesRoot = addResourceRoot("module/src/test/resources", isTestResource = true)
        myFixture.addFileToProject("module/src/test/kotlin/script.kts", "")

        val targets = createKotlinScriptMoveTargets(project, sourceRoot.findChild("script.kts") ?: error("Script file must exist"))

        val resourcesTarget = targets.filterIsInstance<KotlinScriptMoveTarget.Directory>().single { !it.isRecommended }
        assertEquals(testResourcesRoot, resourcesTarget.directory.virtualFile)
    }

    private fun addModuleContentRoot(): VirtualFile {
        val root = createDirectory("module")
        PsiTestUtil.addContentRoot(module, root)
        return root
    }

    private fun addSourceRoot(relativePath: String, isTestSource: Boolean = false): VirtualFile {
        val root = createDirectory(relativePath)
        PsiTestUtil.addSourceRoot(module, root, isTestSource)
        return root
    }

    private fun addResourceRoot(relativePath: String, isTestResource: Boolean = false): VirtualFile {
        val root = createDirectory(relativePath)
        val rootType = if (isTestResource) JavaResourceRootType.TEST_RESOURCE else JavaResourceRootType.RESOURCE
        PsiTestUtil.addSourceRoot(module, root, rootType)
        return root
    }

    private fun createDirectory(relativePath: String): VirtualFile =
        runWriteAction { myFixture.tempDirFixture.findOrCreateDir(relativePath) }
}
