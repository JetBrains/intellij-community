// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PackageDotHtmlMayBePackageInfoInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    myFixture.configureByFile("com/example/package.html");
    myFixture.testHighlighting(true, false, false);
    IntentionAction action = myFixture.findSingleIntention("Convert to 'package-info.java'");
    PsiFile psiFile = myFixture.getFile();
    PsiDirectory directory = psiFile.getContainingDirectory();
    assertTrue(psiFile.isValid());
    myFixture.launchAction(action);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    assertFalse(psiFile.isValid());
    PsiFile packageInfo = directory.findFile("package-info.java");
    assertNotNull(packageInfo);
    assertEquals("""
                   /**
                    * Hello from documentation!
                    * <p>
                    * This is a package.
                    * </p>
                    */
                   package com.example;""", packageInfo.getText());
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PackageDotHtmlMayBePackageInfoInspection();
  }
}