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

  public void testUnusedParameterToConfirmSignature() throws Exception {
    doTest();
  }

  public void testOverloadedMethodSameName() throws Exception {
    doTest();
  }

  public void testSameSignatureMethodExists() throws Exception {
    doTest();
  }

  public void testUnableToCreateStatic() throws Exception {
    doTest();
  }

  public void testRequiredTypeParameter() throws Exception {
    doTest();
  }

  public void testFieldsUsedInsideLambda() throws Exception {
    doTest();
  }

  public void testConvertableToMethodReference() throws Exception {
    assertIntentionNotAvailable();
  }

  public void testNonDenotableParameterTypes() throws Exception {
    assertIntentionNotAvailable();
  }

  public void testEmptyCodeBlock() throws Exception {
    doTest();
  }

  public void testUsedLocalVariables() throws Exception {
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

