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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspection;

public class UnsafeReturnStatementVisitorTest extends LightCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnsafeReturnStatementVisitorInspection());
  }

  public void testInsertVisitLambda() {
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public interface PsiLambdaExpression{}");
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public abstract class JavaRecursiveElementVisitor{}");
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public interface PsiReturnStatement{}");
    myFixture.addClass("package com.intellij.psi;\n" +
                       "public interface PsiClass{}");

    myFixture.configureByText(JavaFileType.INSTANCE, "import com.intellij.psi.*;\n" +
                                                     "class Te<caret>st extends JavaRecursiveElementVisitor {\n" +
                                                     "   public void visitReturnStatement(PsiReturnStatement statement) {}\n" +
                                                     "   public void visitClass(PsiClass aClass) {}\n" +
                                                     "}");
    IntentionAction intention = myFixture.findSingleIntention("Insert visitLambdaExpression method");
    myFixture.launchAction(intention);
    myFixture.checkResult("import com.intellij.psi.*;\n" +
                          "class Test extends JavaRecursiveElementVisitor {\n" +
                          "   public void visitReturnStatement(PsiReturnStatement statement) {}\n" +
                          "   public void visitClass(PsiClass aClass) {}\n" +
                          "\n" +
                          "    public void visitLambdaExpression(PsiLambdaExpression expression) {\n" +
                          "    }\n" +
                          "}");
  }
}
