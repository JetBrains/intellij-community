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
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.TodoCommentInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TodoCommentInspectionTest extends LightJavaInspectionTestCase {

  public void testNoDuplicates() {
    doTest("class A {\n" +
           "// /*TODO comment 'todo fixme'*/todo fixme/**/\n" +
           "}");
  }

  public void testOnlyHighlightLineWithTodo() {
    myFixture.configureByText("X.java",
                              "/**\n" +
                              " * Very useful class\n" +
                              " * <warning descr=\"TODO comment 'TODO: to be or not to be'\">TODO: to be or not to be</warning>\n" +
                              " *\n" +
                              " * @author turbanov\n" +
                              " */\n" +
                              "class WithTodo {}");
    myFixture.testHighlighting(true, false, false);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new TodoCommentInspection();
  }
}