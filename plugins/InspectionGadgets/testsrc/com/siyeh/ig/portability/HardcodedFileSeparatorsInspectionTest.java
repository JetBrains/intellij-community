/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class HardcodedFileSeparatorsInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.net;" +
      "public final class URL implements java.io.Serializable { " +
      "public URL(String spec) throws MalformedURLException { " +
      " this(null, spec);" +
      "}}",

      "package java.net;" +
      "public final class URI implements java.io.Serializable { " +
      "public URI(String str) throws URISyntaxException { " +
      " new Parser(str).parse(false);" +
      "}}",
    };
  }

  public void testHardcodedFileSeparators() {
    doTest();
  }

  public void testNoCrashOnUnclosedLiteral() { doTest();}

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new HardcodedFileSeparatorsInspection();
  }
}