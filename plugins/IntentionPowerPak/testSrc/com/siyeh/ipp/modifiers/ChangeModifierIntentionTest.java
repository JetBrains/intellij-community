/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.modifiers;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public class ChangeModifierIntentionTest extends IPPTestCase {

  public void testMyEnum() { assertIntentionNotAvailable(); }
  public void testMyClass() { assertIntentionNotAvailable(); }
  public void testMyInterface() { assertIntentionNotAvailable(); }
  public void testEnumConstructor() { assertIntentionNotAvailable(); }
  public void testLocalClass() { assertIntentionNotAvailable(); }
  public void testMethod() { doTestWithChooser("private"); }
  public void testMethod2() { doTestWithChooser("package-private"); }
  public void testAnnotatedMember() { doTestWithChooser("private"); }
  public void testClass() { doTestWithChooser("protected"); }
  public void testInDefaultPackage() { doTest("Make package-private"); }

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

  void doTestWithChooser(String wanted) {
    UiInterceptors
      .register(new ChooserInterceptor(Arrays.asList("public", "protected", "package-private", "private"), Pattern.quote(wanted)));
    doTest();
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
