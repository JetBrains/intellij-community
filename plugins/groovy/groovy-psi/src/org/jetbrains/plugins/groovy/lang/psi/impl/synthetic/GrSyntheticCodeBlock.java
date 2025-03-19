// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

/**
 * @author Medvedev Max
 */
public class GrSyntheticCodeBlock extends LightElement implements PsiCodeBlock {
  private static final Logger LOG = Logger.getInstance(GrSyntheticCodeBlock.class);
  private final GrCodeBlock myCodeBlock;
  private static final Key<SoftReference<PsiJavaToken>> PSI_JAVA_TOKEN = Key.create("psi_java_token");

  public GrSyntheticCodeBlock(@NotNull GrCodeBlock codeBlock) {
    super(codeBlock.getManager(), codeBlock.getLanguage());
    myCodeBlock = codeBlock;
  }

  @Override
  public String toString() {
    return "code block wrapper to represent java codeBlock";
  }

  @Override
  public PsiStatement @NotNull [] getStatements() {
    return PsiStatement.EMPTY_ARRAY; //todo return statements
  }

  @Override
  public PsiElement getFirstBodyElement() {
    final PsiElement nextSibling = myCodeBlock.getLBrace().getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  @Override
  public PsiElement getLastBodyElement() {
    final PsiElement rBrace = myCodeBlock.getRBrace();
    if (rBrace != null) {
      final PsiElement prevSibling = rBrace.getPrevSibling();
      return prevSibling == myCodeBlock.getLBrace() ? null : prevSibling;
    }
    return getLastChild();
  }

  @Override
  public PsiJavaToken getLBrace() {
    return getOrCreateJavaToken(myCodeBlock.getLBrace(), JavaTokenType.LBRACE);
  }

  @Override
  public PsiJavaToken getRBrace() {
    return getOrCreateJavaToken(myCodeBlock.getRBrace(), JavaTokenType.RBRACE);
  }

  private static @Nullable PsiJavaToken getOrCreateJavaToken(@Nullable PsiElement element, @NotNull IElementType type) {
    if (element == null) return null;

    final SoftReference<PsiJavaToken> ref = element.getUserData(PSI_JAVA_TOKEN);
    final PsiJavaToken token = dereference(ref);
    if (token != null) return token;
    final LightJavaToken newToken = new LightJavaToken(element, type);
    element.putUserData(PSI_JAVA_TOKEN, new SoftReference<>(newToken));
    return newToken;
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    if (newElement instanceof GrSyntheticCodeBlock other) {
      PsiElement replaced = myCodeBlock.replace(other.myCodeBlock);
      LOG.assertTrue(replaced instanceof GrOpenBlock);
      return PsiImplUtil.getOrCreatePsiCodeBlock((GrOpenBlock)replaced);
    }
    return super.replace(newElement);
  }

  @Override
  public boolean shouldChangeModificationCount(PsiElement place) {
    return false;
  }

  @Override
  public TextRange getTextRange() {
    return myCodeBlock.getTextRange();
  }

  @Override
  public int getStartOffsetInParent() {
    return myCodeBlock.getStartOffsetInParent();
  }

  @Override
  public PsiFile getContainingFile() {
    return myCodeBlock.getContainingFile();
  }

  @Override
  public int getTextOffset() {
    return myCodeBlock.getTextOffset();
  }

  @Override
  public String getText() {
    return myCodeBlock.getText();
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return myCodeBlock;
  }

  @Override
  public boolean isValid() {
    return myCodeBlock.isValid();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    myCodeBlock.delete();
  }
}
