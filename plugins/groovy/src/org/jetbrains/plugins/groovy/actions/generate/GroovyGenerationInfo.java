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
package org.jetbrains.plugins.groovy.actions.generate;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.isWhiteSpace;

/**
 * @author peter
 */
public class GroovyGenerationInfo<T extends PsiMember> extends PsiGenerationInfo<T> {
  private static final Logger LOG = Logger.getInstance(GroovyGenerationInfo.class);

  public GroovyGenerationInfo(@NotNull T member, boolean mergeIfExists) {
    super(member, mergeIfExists);
  }

  public GroovyGenerationInfo(@NotNull T member) {
    super(member);
  }

  @Override
  public void insert(@NotNull PsiClass aClass, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException {
    super.insert(aClass, anchor, before);

    final T member = getPsiMember();
    if (member == null) return;

    LOG.assertTrue(member instanceof GroovyPsiElement);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(member.getProject());

    final PsiElement prev = member.getPrevSibling();
    if (prev!=null && GroovyTokenTypes.mNLS == prev.getNode().getElementType()) {
      prev.replace(factory.createLineTerminator(1));
    }

    final PsiElement next = member.getNextSibling();
    if (next != null && GroovyTokenTypes.mNLS == next.getNode().getElementType()) {
      next.replace(factory.createLineTerminator(1));
    }

    GrReferenceAdjuster.shortenReferences(member);
  }

  @Override
  public PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf) {
    PsiElement parent = aClass instanceof GroovyScriptClass ? aClass.getContainingFile() : ((GrTypeDefinition)aClass).getBody();

    PsiElement element = PsiTreeUtil.findPrevParent(parent, leaf);

    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) {
      return null;
    }
    else {
      PsiElement rBrace = aClass.getRBrace();
      if (!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace)) {
        return null;
      }
    }

    return element;
  }

  @Override
  public void positionCaret(Editor editor, boolean toEditMethodBody) {
    final T firstMember = getPsiMember();
    LOG.assertTrue(firstMember.isValid());

    if (toEditMethodBody) {
      GrMethod method = (GrMethod)firstMember;
      GrOpenBlock body = method.getBlock();
      if (body != null) {
        PsiElement l = body.getLBrace();
        if (l != null) l = l.getNextSibling();
        while (isWhiteSpace(l)) l = l.getNextSibling();
        if (l == null) l = body;

        PsiElement r = body.getRBrace();
        if (r != null) r = r.getPrevSibling();
        while (isWhiteSpace(r)) r = r.getPrevSibling();
        if (r == null) r = body;

        int start = l.getTextRange().getStartOffset();
        int end = r.getTextRange().getEndOffset();

        editor.getCaretModel().moveToOffset(Math.min(start, end));
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        if (start < end) {
          //Not an empty body
          editor.getSelectionModel().setSelection(start, end);
        }
        return;
      }
    }

    int offset;
    if (firstMember instanceof GrMethod) {
      GrMethod method = (GrMethod)firstMember;
      GrCodeBlock body = method.getBlock();
      if (body == null) {
        offset = method.getTextRange().getStartOffset();
      }
      else {
        offset = body.getLBrace().getTextRange().getEndOffset();
      }
    }
    else {
      offset = firstMember.getTextRange().getStartOffset();
    }

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }
}
