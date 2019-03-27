// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.ToolExtensionPoints;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MismatchedCollectionQueryUpdateInspectionTest extends LightInspectionTestCase {

  private static final ImplicitUsageProvider TEST_PROVIDER = new ImplicitUsageProvider() {
    @Override
    public boolean isImplicitUsage(PsiElement element) {
      return false;
    }

    @Override
    public boolean isImplicitRead(PsiElement element) {
      return false;
    }

    @Override
    public boolean isImplicitWrite(PsiElement element) {
      return element instanceof PsiField && "injected".equals(((PsiField)element).getName());
    }
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), ImplicitUsageProvider.EP_NAME, TEST_PROVIDER, myFixture.getTestRootDisposable());
    Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.DEAD_CODE_TOOL).getExtensions();
  }

  public void testMismatchedCollectionQueryUpdate() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9_ANNOTATED;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    MismatchedCollectionQueryUpdateInspection inspection = new MismatchedCollectionQueryUpdateInspection();
    inspection.ignoredClasses.add("com.siyeh.igtest.bugs.mismatched_collection_query_update.ConstList");
    return inspection;
  }
}