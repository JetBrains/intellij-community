// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.actions.ViewOfflineResultsAction
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.offline.OfflineProblemDescriptor
import com.intellij.codeInspection.offlineViewer.OfflineViewParseUtil
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.codeInspection.ui.InspectionTree
import com.intellij.codeInspection.ui.ProblemDescriptionNode
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import java.nio.file.Paths

abstract class AbstractViewOfflineInspectionTest: KotlinLightCodeInsightFixtureTestCase() {
    override fun mainFile(): File = File(testDataDirectory, fileName().replace("_report.xml", ".kt"))

    fun doTest(path: String) {
        val testDataFile = dataFile()
        val testPath = testDataFile.toString()
        val shortName = run {
            val parent = testDataFile.parentFile.name
            parent[0].toUpperCase() + parent.substring(1)
        }

        InspectionProfileImpl.INIT_INSPECTIONS = true
        val profile = InspectionProfileImpl("test").also {
            it.initInspectionTools(project)
        }

        val ktFile = mainFile()
        val ktFileName = ktFile.toString()
        myFixture.configureByFile(ktFileName) as KtFile

        val offlineReportText = with(FileUtil.loadFile(ktFile, true)) {
            assertTrue("\"<caret>\" is missing in file \"$ktFileName\"", this.contains("<caret>"))
            assertTrue("\"<selection>\" is missing in file \"$ktFileName\"", this.contains("<selection>"))
            InTextDirectivesUtils.stringWithDirective(this, "OFFLINE_REPORT")
        }

        val selectionStart = myFixture.editor.selectionModel.selectionStart
        // reset caret
        myFixture.editor.caretModel.currentCaret.moveToOffset(0)

        val view =
            ViewOfflineResultsAction.showOfflineView(project, mapOf(shortName to parse(testPath)), profile, "")
        try {
            profile.initInspectionTools(project)

            view.globalInspectionContext.uiOptions.SHOW_STRUCTURE = true
            val tree = updateTree(view)

            val problemDescriptionNode = expandAll(tree)
            assertEquals(offlineReportText, problemDescriptionNode.presentableText)
            HeadlessDataManager.fallbackToProductionDataManager(view)
            val navigatable = CommonDataKeys.NAVIGATABLE.getData(DataManager.getInstance().getDataContext(view))!!
            assertTrue(navigatable.canNavigate())
            assertTrue(navigatable.canNavigateToSource())
            navigatable.navigate(true)
            assertEquals(selectionStart, myFixture.editor.caretModel.currentCaret.offset)
        } finally {
            Disposer.dispose(view)
            InspectionProfileImpl.INIT_INSPECTIONS = false
        }
    }

    private fun expandAll(tree: InspectionTree): ProblemDescriptionNode {
        while (true) {
            UIUtil.dispatchAllInvocationEvents()
            val expandedPaths = TreeUtil.collectExpandedPaths(tree)
            tree.setSelectionRow(expandedPaths.size)
            val selectionPath = tree.selectionModel.selectionPath
            if (selectionPath == null) continue
            return selectionPath.lastPathComponent.cast<ProblemDescriptionNode>()
        }
    }

    private fun parse(path: String): Map<String, Set<OfflineProblemDescriptor>> {
        val moduleName = module.name
        val descriptors = OfflineViewParseUtil.parse(Paths.get(path))
        for (problemDescriptors in descriptors.values) {
            for (descriptor in problemDescriptors) {
                descriptor.setModule(moduleName)
            }
        }
        return descriptors
    }

    private fun updateTree(view: InspectionResultsView): InspectionTree {
        view.update()
        view.dispatchTreeUpdate()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val tree: InspectionTree = view.tree
        PlatformTestUtil.expandAll(tree)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return tree
    }
}