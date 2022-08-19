// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.modifiers;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public class ChangeModifierIntentionTest extends IPPTestCase {

  public void testMyEnum() { assertIntentionNotAvailable(); }
  public void testMyClass() { assertIntentionNotAvailable(); }
  public void testMyInterface() { assertIntentionNotAvailable(); }
  public void testMyInterfaceJava9() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> assertIntentionNotAvailable("Make 'm()' private"));
  }
  public void testMyInterfaceDefaultJava9() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> doTest("Make 'm()' private"));
  }
  public void testMyInterfacePrivateJava9() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> doTest("Make 'm()' public"));
  }
  public void testMyInterfacePrivateStaticJava9() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> doTest("Make 'm()' public"));
  }
  public void testEnumConstructor() { assertIntentionNotAvailable(); }
  public void testLocalClass() { assertIntentionNotAvailable(); }
  public void testMethod() { doTestWithChooser("private"); }
  public void testMethod2() { doTestWithChooser("package-private"); }
  public void testAnnotatedMember() { doTestWithChooser("private"); }
  public void testClass() { doTestWithChooser("protected"); }
  public void testInDefaultPackage() { doTest("Make 'Test' package-private"); }
  public void testInheritance() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest("Make 'foo()' public"));
  }

  public void testTypeParameter() {
    doTestWithChooser("protected");
  }

  public void testAccessConflict() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    try {
      doTestWithChooser("protected");
      fail("Must have an exception");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(
        "method <b><code>fooooooo()</code></b> will have incompatible access privileges with overriding method <b><code>Y.fooooooo()</code></b>",
        e.getMessage());
    }
  }

  public void testAccessConflictIgnore() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTestWithChooser("protected"));
  }

  public void testRecordConstructor1() { 
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, () -> doTest("Make 'Foo()' protected"));
  }

  public void testRecordConstructor2() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, () -> doTestWithChooser("public"));
  }
  
  void doTestWithChooser(String wanted) {
    UiInterceptors
      .register(new ChooserInterceptor(Arrays.asList("public", "protected", "package-private", "private"), Pattern.quote(wanted)));
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getRelativePath() {
    return "modifiers/change_modifier";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("change.modifier.intention.name");
  }
}
