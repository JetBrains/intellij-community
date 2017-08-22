/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ipp.functional;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ExtractToMethodReferenceTest extends IPPTestCase {

  public void testUnusedParameterToConfirmSignature() {
    doTest();
  }

  public void testOverloadedMethodSameName() {
    doTest();
  }

  public void testSameSignatureMethodExists() {
    doTest();
  }

  public void testUnableToCreateStatic() {
    doTest();
  }

  public void testRequiredTypeParameter() {
    doTest();
  }

  public void testFieldsUsedInsideLambda() {
    doTest();
  }

  public void testConvertableToMethodReference() {
    assertIntentionNotAvailable();
  }

  public void testNonDenotableParameterTypes() {
    assertIntentionNotAvailable();
  }

  public void testEmptyCodeBlock() {
    doTest();
  }

  public void testUsedLocalVariables() {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("extract.to.method.reference.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "functional/extractToMethodReference";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}

