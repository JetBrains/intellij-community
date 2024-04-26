// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.codeInsight.AbstractLineMarkersTest
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.SuperDeclarationPopupHandler
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.idea.navigation.NavigationTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.junit.Assert
import kotlin.io.path.Path

abstract class AbstractLineMarkersK2Test : AbstractLineMarkersTest() {
    override fun doTest(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Path(path), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(path)
        }
    }

    override fun getActualNavigationDataAndCompare(lineMarker: LineMarkerInfo<*>, navigationComment: String) {
        when (val handler = lineMarker.navigationHandler) {
            is InheritorsLineMarkerNavigator -> {
                val gotoData =
                    GotoImplementationHandler().createDataForSourceForTests(editor, lineMarker.element!!.parent!!)
                val targets = gotoData.targets.toMutableList().sortedBy {
                    it.renderAsGotoImplementation()
                }
                val actualNavigationData = NavigationTestUtils.getNavigateElementsText(project, targets)

                UsefulTestCase.assertSameLines(getExpectedNavigationText(navigationComment), actualNavigationData)

            }

            is SuperDeclarationPopupHandler -> {
                val ktDeclaration = lineMarker.element!!.getParentOfType<KtDeclaration>(false)!!

                val superDeclarations =
                    KotlinGoToSuperDeclarationsHandler.findSuperDeclarations(ktDeclaration)!!.items.map { it.declaration.element }

                val actualNavigationData = NavigationTestUtils.getNavigateElementsText(project, superDeclarations)

                UsefulTestCase.assertSameLines(getExpectedNavigationText(navigationComment), actualNavigationData)

            }

            else -> {
                Assert.fail("${handler} is not supported for navigate check")
            }
        }
    }

    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

}