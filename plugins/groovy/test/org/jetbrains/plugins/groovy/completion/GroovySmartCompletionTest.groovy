/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.completion;


import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class GroovySmartCompletionTest extends GroovyCompletionTestBase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/smart";
  }

  public void testSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    assertOrderedEquals(myFixture.getLookupElementStrings(), "Bar", "Foo");
  }

  public void testSmartCompletionAfterNewInDeclarationWithInterface() throws Throwable { doSmartTest(); }

  public void testCaretAfterSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithAbstractClass() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithArray() throws Throwable { doSmartTest(); }

  public void testSmartCompletionAfterNewInDeclarationWithIntArray() throws Throwable { doSmartTest(); }

  public void testShortenNamesInSmartCompletionAfterNewInDeclaration() throws Throwable { doSmartTest(); }

  public void testSmartAfterNewInCall() throws Throwable { doSmartTest(); }

  public void testInnerClassInStaticMethodCompletion() throws Throwable { doSmartTest(); }

  public void testSmartCompletionInAssignmentExpression() throws Throwable { doSmartTest(); }

  public void testSimpleMethodParameter() throws Throwable {
    doSmartCompletion("d1", "d2");
  }

  public void testReturnStatement() throws Exception {
    doSmartCompletion("b", "b1", "b2", "foo");
  }

  public void testIncSmartCompletion() throws Exception {
    doSmartCompletion("a", "b");
  }

  public void testInheritConstructorsAnnotation() throws Throwable {
    myFixture.addFileToProject("groovy/transform/InheritConstructors.java", "package groovy.transform;\n" +
                                                                            "\n" +
                                                                            "import java.lang.annotation.ElementType;\n" +
                                                                            "import java.lang.annotation.Retention;\n" +
                                                                            "import java.lang.annotation.RetentionPolicy;\n" +
                                                                            "import java.lang.annotation.Target;@Retention(RetentionPolicy.SOURCE)\n" +
                                                                            "@Target({ElementType.TYPE})\n" +
                                                                            "public @interface InheritConstructors {\n" +
                                                                            "}");
    doSmartTest();
  }

  public void testSmartCastCompletion() {doSmartTest();}
  public void testSmartCastCompletionWithoutRParenth() {doSmartTest();}
  public void testSmartCastCompletionWithRParenth() {doSmartTest();}

  public void testDontCompletePrivateMembers() {doSmartCompletion "foo1", "foo2"}

  public void testEnumMembersInAssignment() {doSmartCompletion "IN_STOCK", "NOWHERE", "ORDERED" }
  public void testEnumMembersInAssignmentInsideEnum() {doSmartCompletion "IN_STOCK", "NOWHERE", "ORDERED", "next", "previous" }

  void testNativeList() {doSmartCompletion('a1', 'a2')};

  def getFileText(PsiFile file) {
    return PsiDocumentManager.getInstance(project).getDocument(file).text
  }
}
