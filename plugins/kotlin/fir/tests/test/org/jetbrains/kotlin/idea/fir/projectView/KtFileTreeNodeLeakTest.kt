// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.projectView.KtFileTreeNode
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Checks that a [KtFileTreeNode] in the Project View tree holds no [KtFile] through a strong reference.
 *
 * The check does not follow the fields of the project, of a virtual file, of the view settings and of a parent node.
 * These objects are shared state of the IDE and reach every PSI file.
 */
@TestRoot("fir/tests")
@TestDataPath("\$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners::class)
@TestMetadata("../../idea/tests/testData/projectView")
class KtFileTreeNodeLeakTest : AbstractK2ProjectViewTest() {
    @TestMetadata("SimpleKotlinClass.test")
    fun testSimpleKotlinClass() {
        KotlinTestUtils.runTest(this::doTest, this, "../../idea/tests/testData/projectView/SimpleKotlinClass.test")
    }

    override fun doMultiFileTest(testDataPath: String, globalDirectives: Directives) {
        val fileName = globalDirectives.getValue("PATH")
        val virtualFile = FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project)).single()

        val treeStructure = TestProjectTreeStructure(project, testRootDisposable)
        treeStructure.isShowMembers = globalDirectives.getBooleanValue("SHOW_MEMBERS")
        val pane = treeStructure.createPane()
        pane.select(virtualFile.toPsiFile(project), virtualFile, true)
        val tree = pane.tree
        PlatformTestUtil.waitWhileBusy(tree)

        val treeNode = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) { it.userObject is KtFileTreeNode }
                       ?: error("No KtFileTreeNode in the tree")
        val fileNode = treeNode.userObject as KtFileTreeNode
        assertInstanceOf(fileNode.value, KtFile::class.java)

        LeakHunter.checkLeak(
            Supplier { mapOf<Any, String>(fileNode to "KtFileTreeNode") },
            KtFile::class.java,
            Predicate { field: Field -> sharedStateTypes.none { it.isAssignableFrom(field.type) } },
            null,
        )
    }

    private val sharedStateTypes: List<Class<*>> = listOf(
        Project::class.java,
        VirtualFile::class.java,
        ViewSettings::class.java,
        NodeDescriptor::class.java,
    )
}
