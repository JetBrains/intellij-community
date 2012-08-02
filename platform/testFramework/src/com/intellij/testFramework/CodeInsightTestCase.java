/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * User: Andrey.Vokin
 * Date: 7/23/12
 */
public class CodeInsightTestCase extends LightPlatformCodeInsightFixtureTestCase {
  @NonNls protected static final String CARET_STR = "<caret>";

  protected int findOffsetBySignature(String signature) {
    return findOffsetBySignature(signature, myFixture.getFile());
  }

  protected int findOffsetBySignature(String signature, final PsiFile psiFile) {
    final String caretSignature = CARET_STR;
    int caretOffset = signature.indexOf(caretSignature);
    assert caretOffset >= 0;
    signature = signature.substring(0, caretOffset) + signature.substring(caretOffset + caretSignature.length());
    int pos = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile).getText().indexOf(signature);
    assertTrue(pos >= 0);
    return pos + caretOffset;
  }

  protected PsiReference findReferenceBySignature(final String signature) {
    final int offset = findOffsetBySignature(signature);
    return myFixture.getFile().findReferenceAt(offset);
  }
}
