/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {

  public GrNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "NEW expression";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitNewExpression(this);
  }

  public PsiType getType() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) {
      return anonymous.getBaseClassType();
    }
    PsiType type = null;
    GrCodeReferenceElement refElement = getReferenceElement();
    if (refElement != null) {
      type = new GrClassReferenceType(refElement);
    } else {
      GrBuiltInTypeElement builtin = findChildByClass(GrBuiltInTypeElement.class);
      if (builtin != null) type = builtin.getType();
    }

    if (type != null) {
      for (int i = 0; i < getArrayCount(); i++) {
        type = type.createArrayType();
      }
      return type;
    }

    return null;
  }

  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    final GrArgumentList list = getArgumentList();
    if (list == null) { //so it is not anonymous class declaration
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createExpressionArgumentList();
      PsiElement last = getLastChild();
      while (last.getPrevSibling() instanceof PsiWhiteSpace || last.getPrevSibling() instanceof PsiErrorElement) {
        last = last.getPrevSibling();
      }
      ASTNode astNode = last.getNode();
      assert astNode != null;
      getNode().addChild(newList.getNode(), astNode);
    }
    return super.addNamedArgument(namedArgument);
  }

  @Override
  public GrArgumentList getArgumentList() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getArgumentListGroovy();
    return super.getArgumentList();
  }

  @Nullable
  public GrExpression getQualifier() {
    final PsiElement[] children = getChildren();
    for (PsiElement child : children) {
      if (child instanceof GrExpression) return (GrExpression)child;
      if (PsiKeyword.NEW.equals(child.getText())) return null;
    }
    return null;
  }

  public GrCodeReferenceElement getReferenceElement() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getBaseClassReferenceGroovy();
    return findChildByClass(GrCodeReferenceElement.class);
  }

  @NotNull
  public GroovyResolveResult[] multiResolveConstructor() {
    GrCodeReferenceElement ref = getReferenceElement();
    if (ref == null) return GroovyResolveResult.EMPTY_ARRAY;

    final GroovyResolveResult[] classResults = ref.multiResolve(false);
    if (classResults.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

    if (getNamedArguments().length > 0 && getArgumentList().getExpressionArguments().length == 0) {
      GroovyResolveResult[] constructorResults = PsiUtil.getConstructorCandidates(ref, classResults, new PsiType[]{PsiUtil.createMapType(getManager(), getResolveScope())}); //one Map parameter, actually
      for (GroovyResolveResult result : constructorResults) {
        if (result.getElement() instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)result.getElement();
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();
          if (parameters.length == 1 && InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) {
            return constructorResults;
          }
        }
      }
      final GroovyResolveResult[] emptyConstructors = PsiUtil.getConstructorCandidates(ref, classResults, PsiType.EMPTY_ARRAY);
      if (emptyConstructors.length > 0) {
        return emptyConstructors;
      }
    }

    return PsiUtil.getConstructorCandidates(ref, classResults, PsiUtil.getArgumentTypes(ref, false));
  }

  public GroovyResolveResult[] multiResolveClass() {
    return getReferenceElement().multiResolve(false);
  }

  public PsiMethod resolveConstructor() {
    return PsiImplUtil.extractUniqueElement(multiResolveConstructor());
  }

  @NotNull
  public GroovyResolveResult resolveConstructorGenerics() {
    return PsiImplUtil.extractUniqueResult(multiResolveConstructor());
  }

  public int getArrayCount() {
    final GrArrayDeclaration arrayDeclaration = findChildByClass(GrArrayDeclaration.class);
    if (arrayDeclaration == null) return 0;
    return arrayDeclaration.getArrayCount();
  }

  public GrAnonymousClassDefinition getAnonymousClassDefinition() {
    return findChildByClass(GrAnonymousClassDefinition.class);
  }

  @Nullable
  public PsiMethod resolveMethod() {
    return resolveConstructor();
  }

  @NotNull
  public GroovyResolveResult[] getMethodVariants() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return GroovyResolveResult.EMPTY_ARRAY;
    final GroovyResolveResult[] classResults = referenceElement.multiResolve(false);
    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(getProject()).getResolveHelper();
    for (GroovyResolveResult classResult : classResults) {
      final PsiElement element = classResult.getElement();
      if (element instanceof PsiClass) {
        final PsiMethod[] constructors = ((PsiClass)element).getConstructors();
        for (PsiMethod constructor : constructors) {
          boolean isAccessible = helper.isAccessible(constructor, this, null);
          result.add(new GroovyResolveResultImpl(constructor, null, classResult.getSubstitutor(), isAccessible, true));
        }
      }
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }
}
