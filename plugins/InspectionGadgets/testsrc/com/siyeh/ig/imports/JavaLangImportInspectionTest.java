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
package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class JavaLangImportInspectionTest extends LightInspectionTestCase {

  public void testSamePackageConflict() {
    addEnvironmentClass("package a;" +
                        "class String {}");
    doTest("package a;" +
           "import java.lang.String;" +
           "class X {{" +
           "  String s;" +
           "}}");
  }

  public void testSimple() {
    doTest("package a;" +
           "/*Unnecessary import from package 'java.lang'*/import java.lang.String;/**/" +
           "class X {{" +
           "  String s;" +
           "}}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new JavaLangImportInspection();
  }
}