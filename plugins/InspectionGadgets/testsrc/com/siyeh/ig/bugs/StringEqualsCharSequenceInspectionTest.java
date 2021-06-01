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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("StringEqualsCharSequence")
public class StringEqualsCharSequenceInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doMemberTest("boolean m(String s, CharSequence cs) {" +
                 "  return s./*'String.equals()' called with 'CharSequence' argument*/equals/**/(cs);" +
                 "}");
  }

  public void testMethodReference() {
    doStatementTest("java.util.function.Predicate<CharSequence> p = \"123\"::/*'String.equals()' called with 'CharSequence' argument*/equals/**/;");
  }

  public void testObjectsEquals() {
    doMemberTest("boolean m(String s, CharSequence cs) {" +
                 "  return java.util.Objects./*'String.equals()' called with 'CharSequence' argument*/equals/**/(s, cs);" +
                 "}");
  }

  public void testPredicate() {
    doMemberTest("boolean m(String s, CharSequence cs) {" +
                 "  return java.util.function.Predicate./*'String.equals()' called with 'CharSequence' argument*/isEqual/**/(s).test(cs);" +
                 "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringEqualsCharSequenceInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}