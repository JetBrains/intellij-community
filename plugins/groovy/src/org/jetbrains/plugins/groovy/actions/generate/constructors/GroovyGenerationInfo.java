/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.actions.generate.constructors;

import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author peter
 */
public class GroovyGenerationInfo<T extends PsiMember> extends PsiGenerationInfo<T>{
  public GroovyGenerationInfo(@NotNull T member) {
    super(member);
  }

  @Override
  public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    super.insert(aClass, anchor, before);
  }

  @Override
  public PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf) {
    PsiElement element = leaf;
    if (element.getParent() != aClass) {
      while (element.getParent().getParent() != aClass) {
        element = element.getParent();
      }
    }

    final GrTypeDefinition typeDefinition = (GrTypeDefinition)aClass;
    PsiElement lBrace = typeDefinition.getLBraceGroovy();
    if (lBrace == null) {
      return null;
    }
    else {
      PsiElement rBrace = typeDefinition.getRBraceGroovy();
      if (!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace)) {
        return null;
      }
    }

    final IElementType type = element.getNode().getElementType();
    if (type == GroovyTokenTypes.mNLS || type == GroovyTokenTypes.mWS) {
      return element.getNextSibling();
    }

    return element;
  }
}
