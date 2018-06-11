// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrNamedArgumentSearchVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

public class GrFieldImpl extends GrVariableBaseImpl<GrFieldStub> implements GrField, StubBasedPsiElement<GrFieldStub> {

  public GrFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrFieldImpl(GrFieldStub stub) {
    this(stub, GroovyElementTypes.FIELD);
  }

  public GrFieldImpl(GrFieldStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  public String toString() {
    return "Field";
  }

  @Override
  public PsiExpression getInitializer() {
    return org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getOrCreatePisExpression(getInitializerGroovy());
  }

  @Override
  public void setInitializer(@Nullable PsiExpression psiExpression) throws IncorrectOperationException {
    GrExpression oldInitializer = getInitializerGroovy();
    if (psiExpression == null) {
      if (oldInitializer != null) {
        oldInitializer.delete();
        PsiElement assign = findChildByType(GroovyTokenTypes.mASSIGN);
        if (assign != null) {
          assign.delete();
        }
      }
      return;
    }


    GrExpression newInitializer = GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(psiExpression.getText());
    if (oldInitializer != null) {
      oldInitializer.replaceWithExpression(newInitializer, true);
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mASSIGN, "=", getNode().getLastChildNode());
      addAfter(newInitializer, getLastChild());
    }
  }

  @Override
  public boolean isDeprecated() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecatedByDocTag() || PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiType getTypeGroovy() {
    PsiType type = TypeInferenceHelper.getCurrentContext().getExpressionType(this, field -> {
      if (getDeclaredType() == null && getInitializerGroovy() == null) {
        final PsiType type1 = GrVariableEnhancer.getEnhancedType(field);
        if (type1 != null) {
          return type1;
        }
      }
      return null;
    });

    if (type != null) {
      return type;
    }

    return super.getTypeGroovy();
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent().getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }

    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase)file).getScriptClass();
    }

    return null;
  }

  @Override
  public boolean isProperty() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      return stub.isProperty();
    }
    return PsiUtil.isProperty(this);
  }

  @Nullable
  @Override
  public GrAccessorMethod getSetter() {
    return GrClassImplUtil.findSetter(this);
  }

  @Override
  @NotNull
  public GrAccessorMethod[] getGetters() {
    return GrClassImplUtil.findGetters(this);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    if (isProperty()) {
      return ResolveScopeManager.getElementUseScope(this); //maximal scope
    }
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getFieldPresentation(this);
  }

  @Override
  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass)containingClass.getOriginalElement();
    PsiField originalField = originalClass.findFieldByName(getName(), false);
    return originalField != null ? originalField : this;
  }

  @Nullable
  @Override
  protected Icon getElementIcon(@IconFlags int flags) {
    boolean isAbstract = hasModifierProperty(PsiModifier.ABSTRACT);
    Icon fieldIcon = isProperty()
                     ? isAbstract ? JetgroovyIcons.Groovy.AbstractProperty : JetgroovyIcons.Groovy.Property
                     : isAbstract ? JetgroovyIcons.Groovy.AbstractField : JetgroovyIcons.Groovy.Field;
    return ElementPresentationUtil.createLayeredIcon(fieldIcon, this, false);
  }

  @Override
  @NotNull
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      String[] namedParameters = stub.getNamedParameters();
      if (namedParameters.length == 0) return Collections.emptyMap();

      Map<String, NamedArgumentDescriptor> result = ContainerUtil.newHashMap();
      for (String parameter : namedParameters) {
        result.put(parameter, GrNamedArgumentSearchVisitor.CODE_NAMED_ARGUMENTS_DESCR);
      }
      return result;
    }

    return GrNamedArgumentSearchVisitor.find(this);
  }

  @Override
  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }
}
