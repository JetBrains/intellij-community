package com.intellij.testFramework;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * User: Andrey.Vokin
 * Date: 7/23/12
 */
public class CodeInsightTestCase extends LightPlatformCodeInsightFixtureTestCase {
  @NonNls protected static final String CARET_STR = "<caret>";

  protected CodeInsightTestFixture myFixture;

  protected Project getProject() {
    return myFixture.getProject();
  }

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

  protected PsiElement findPsiBySignature(final String signature) {
    return findPsiBySignature(signature, myFixture.getFile());
  }

  protected PsiElement findPsiBySignature(final String signature, final PsiFile psiFile) {
    final int offset = findOffsetBySignature(signature, psiFile);
    return psiFile.findElementAt(offset);
  }

  protected PsiElement findPsiBySignature(final String signature, final VirtualFile file) {
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    final int offset = findOffsetBySignature(signature, psiFile);
    return psiFile.findElementAt(offset);
  }
}
