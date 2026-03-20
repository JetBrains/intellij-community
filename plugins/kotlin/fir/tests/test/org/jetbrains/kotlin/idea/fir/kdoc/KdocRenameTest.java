// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.kdoc;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NotNull;import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

@TestRoot("idea/tests")
@TestMetadata("testData/kdoc/rename")
@RunWith(JUnit38ClassRunner.class)
public class KdocRenameTest extends KotlinLightCodeInsightFixtureTestCase {
  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  public void testParamReference() {
        doTest("bar");
    }

    public void testTypeParamReference() {
        doTest("R");
    }

    public void testCodeReference() {
        doTest("xyzzy");
    }

  private void doTest(String newName) {
        myFixture.configureByFile(getTestName(false) + ".kt");
        PsiElement element = TargetElementUtil
                .findTargetElement(getEditor(),
                                   TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
        assertNotNull(element);
        new RenameProcessor(getProject(), element, newName, true, true).run();
        myFixture.checkResultByFile(getTestName(false) + ".kt.after");
    }
}
