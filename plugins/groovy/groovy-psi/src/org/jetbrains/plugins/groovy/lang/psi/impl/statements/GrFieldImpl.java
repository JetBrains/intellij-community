/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Function;
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
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
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
  public PsiElement getParent() {
    return getParentByStub();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  @Override
  public GrTypeElement getTypeElementGroovy() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      final String typeText = stub.getTypeText();
      if (typeText == null) {
        return null;
      }

      return GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeText, this);
    }

    return super.getTypeElementGroovy();
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
    PsiType type = TypeInferenceHelper.getCurrentContext().getExpressionType(this, new Function<GrFieldImpl, PsiType>() {
      @Override
      public PsiType fun(GrFieldImpl field) {
        if (getDeclaredType() == null && getInitializerGroovy() == null) {
          final PsiType type = GrVariableEnhancer.getEnhancedType(field);
          if (type != null) {
            return type;
          }
        }
        return null;
      }
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

  @Override
  public GrAccessorMethod getSetter() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<GrAccessorMethod>() {
      @Nullable
      @Override
      public Result<GrAccessorMethod> compute() {
        return Result.create(GrAccessorMethodImpl.createSetterMethod(GrFieldImpl.this), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  @Override
  @NotNull
  public GrAccessorMethod[] getGetters() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<GrAccessorMethod[]>() {
      @Nullable
      @Override
      public Result<GrAccessorMethod[]> compute() {
        return Result.create(GrAccessorMethodImpl.createGetterMethods(GrFieldImpl.this), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    if (isProperty()) {
      return ResolveScopeManager.getElementUseScope(this); //maximal scope
    }
    return PsiImplUtil.getMemberUseScope(this);
  }

  @NotNull
  @Override
  public String getName() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
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
    Icon superIcon = JetgroovyIcons.Groovy.Field;
    if (!isProperty()) return superIcon;
    LayeredIcon rowIcon = new LayeredIcon(2);
    rowIcon.setIcon(superIcon, 0);
    rowIcon.setIcon(JetgroovyIcons.Groovy.Def, 1);
    return rowIcon;
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
