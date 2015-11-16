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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspectionTest extends LightInspectionTestCase {

  public void testSplitDeclarationAssignment() {
    doMemberTest("void foo() {\n" +
                 "     RuntimeException exception = null;\n" +
                 "     try {\n" +
                 "         new java.io.FileInputStream(\"asdf\");\n" +
                 "     } catch (java.io.FileNotFoundException e) {\n" +
                 "         exception = new RuntimeException();\n" +
                 "         exception./*Unnecessary 'Throwable.initCause()' call*/initCause/**/(e);\n" +
                 "     } catch (RuntimeException e) {\n" +
                 "         exception = e;\n" +
                 "     }\n" +
                 "     throw exception;\n" +
                 "}");
  }

  public void testReassigned() {
    doMemberTest("void foo() {\n" +
                 "    try {\n" +
                 "        new java.io.FileInputStream(\"asdf\");\n" +
                 "    } catch (java.io.FileNotFoundException e) {\n" +
                 "        RuntimeException exception = new RuntimeException();\n" +
                 "        e = null;\n" +
                 "        exception.initCause(e);\n" +
                 "        throw exception;\n" +
                 "    }\n" +
                 "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryInitCauseInspection();
  }
}