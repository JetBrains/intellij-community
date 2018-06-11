// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;

public class GrAnnotationArgumentListImpl extends GrStubElementBase<EmptyStub>
  implements GrAnnotationArgumentList, StubBasedPsiElement<EmptyStub> {

  private static final Logger LOG = Logger.getInstance(GrAnnotationArgumentListImpl.class);

  public GrAnnotationArgumentListImpl(@NotNull EmptyStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION_ARGUMENTS);
  }

  public GrAnnotationArgumentListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAnnotationArgumentList(this);
  }

  public String toString() {
    return "Annotation arguments";
  }

  @Override
  @NotNull
  public GrAnnotationNameValuePair[] getAttributes() {
    return getStubOrPsiChildren(GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR, GrAnnotationNameValuePair.EMPTY_ARRAY);
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first.getElementType() == GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR && last.getElementType() ==
                                                                                     GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR) {
      ASTNode lparenth = getNode().getFirstChildNode();
      ASTNode rparenth = getNode().getLastChildNode();
      if (lparenth == null) {
        getNode().addLeaf(GroovyTokenTypes.mLPAREN, "(", null);
      }
      if (rparenth == null) {
        getNode().addLeaf(GroovyTokenTypes.mRPAREN, ")", null);
      }

      final PsiNameValuePair[] nodes = getAttributes();
      if (nodes.length == 1) {
        final PsiNameValuePair pair = nodes[0];
        if (pair.getName() == null) {
          final String text = pair.getValue().getText();
          try {
            final PsiAnnotation annotation = GroovyPsiElementFactory.getInstance(getProject()).createAnnotationFromText("@AAA(value = " + text + ")");
            getNode().replaceChild(pair.getNode(), annotation.getParameterList().getAttributes()[0].getNode());
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

      if (anchor == null && before != null) {
        anchor = before.booleanValue() ? getNode().getLastChildNode() : getNode().getFirstChildNode();
      }
    }

    return super.addInternal(first, last, anchor, before);
  }
}
