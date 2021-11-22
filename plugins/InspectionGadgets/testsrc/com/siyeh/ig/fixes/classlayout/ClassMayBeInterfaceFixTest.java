// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.classlayout;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.classlayout.ClassMayBeInterfaceInspection;

/**
 * @author Bas Leijdekkers
 */
public class ClassMayBeInterfaceFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final ClassMayBeInterfaceInspection inspection = new ClassMayBeInterfaceInspection();
    inspection.reportClassesWithNonAbstractMethods = true;
    myFixture.enableInspections(inspection);
    myRelativePath = "classlayout/class_may_be_interface";
    myDefaultHint = InspectionGadgetsBundle.message("class.may.be.interface.convert.quickfix");
  }

  public void testConvertMe() { doTest(); }
  public void testObjectMethods() { doTest(); }
  public void testDefaultConstructor() { doTest(); }
  public void testLocalClass() { 
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_14, this::assertQuickfixNotAvailable); 
  }
  public void testLocalClassJava15() { 
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.HIGHEST, this::doTest); 
  }
}