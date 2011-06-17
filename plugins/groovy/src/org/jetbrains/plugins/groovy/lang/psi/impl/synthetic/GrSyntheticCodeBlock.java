/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

/**
 * @author Medvedev Max
 */
public class GrSyntheticCodeBlock extends LightElement implements PsiCodeBlock {
  private GrCodeBlock myCodeBlock;
  private static final Key<SoftReference<PsiJavaToken>> PSI_JAVA_TOKEN = Key.create("psi_java_token");

  public GrSyntheticCodeBlock(@NotNull GrCodeBlock codeBlock) {
    super(codeBlock.getManager(), codeBlock.getLanguage());
    myCodeBlock = codeBlock;
  }

  @Override
  public String toString() {
    return "code block wrapper to represent java codeBlock";
  }

  @NotNull
  @Override
  public PsiStatement[] getStatements() {
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

  @Nullable
  private static PsiJavaToken getOrCreateJavaToken(@Nullable PsiElement element, @NotNull IElementType type) {
    if (element == null) return null;

    final SoftReference<PsiJavaToken> ref = element.getUserData(PSI_JAVA_TOKEN);
    final PsiJavaToken token = ref == null ? null : ref.get();
    if (token != null) return token;
    final LightJavaToken newToken = new LightJavaToken(element, type);
    element.putUserData(PSI_JAVA_TOKEN, new SoftReference<PsiJavaToken>(newToken));
    return newToken;
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

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myCodeBlock;
  }

  @Override
  public boolean isValid() {
    return myCodeBlock.isValid();
  }
}
