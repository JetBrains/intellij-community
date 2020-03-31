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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import junit.framework.TestCase;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeCompletionTest extends BasePlatformTestCase {

  @Override
  protected String getBasePath() {
    return "platform/platform-tests/testData/codeInsight/completion/customFileType/";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  public void testKeyWordCompletion() {
    myFixture.configureByFile("1.cs");
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile("1_after.cs");

    myFixture.configureByFile("2.cs");
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile("2_after.cs");
  }

  public void testWordCompletion() {
    myFixture.configureByFile("WordCompletion.cs");
    myFixture.complete(CompletionType.BASIC);
    myFixture.assertPreferredCompletionItems(0, "while", "whiwhiwhi");
  }

  public void testErlang() {
    myFixture.configureByFile("Erlang.erl");
    myFixture.complete(CompletionType.BASIC);
    myFixture.assertPreferredCompletionItems(0, "case", "catch");
  }

  public void testComment() {
    myFixture.configureByFile("foo.cs");
    myFixture.complete(CompletionType.BASIC);
    UsefulTestCase.assertEmpty(myFixture.getLookupElements());
  }

  public void testEmptyFile() {
    myFixture.configureByText("a.cs", "<caret>");
    myFixture.complete(CompletionType.BASIC);
    TestCase.assertTrue(myFixture.getLookupElementStrings().contains("abstract"));
    TestCase.assertFalse(myFixture.getLookupElementStrings().contains("x"));
  }

  public void testPlainTextSubstitution() {
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.xxx", MockLanguageFileType.INSTANCE, "aaa a<caret>", 0, true);
    myFixture.configureFromExistingVirtualFile(file.getViewProvider().getVirtualFile());
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResult("aaa aaa<caret>");
  }

}
