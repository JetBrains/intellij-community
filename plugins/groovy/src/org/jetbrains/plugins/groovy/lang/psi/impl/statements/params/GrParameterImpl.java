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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrParameterImpl extends GrVariableImpl implements GrParameter {
  public GrParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "Parameter";
  }

  @Nullable
  public PsiType getTypeGroovy() {
    GrTypeElement typeElement = getTypeElementGroovy();
    if (typeElement != null) {
      PsiType type = typeElement.getType();
      if (!isVarArgs()) {
        return type;
      }
      else {
        return new PsiEllipsisType(type);
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (isVarArgs()) {
      PsiClassType type = factory.createTypeByFQClassName("java.lang.Object", getResolveScope());
      return new PsiEllipsisType(type);
    }
    PsiElement parent = getParent();
    if (parent instanceof GrForInClause) {
      GrExpression iteratedExpression = ((GrForInClause)parent).getIteratedExpression();
      if (iteratedExpression instanceof GrRangeExpression) {
        return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_INTEGER, getResolveScope());
      }
      else if (iteratedExpression != null) {
        PsiType result = findTypeForCollection(iteratedExpression, factory, this);
        if (result != null) {
          return result;
        }
      }
    } else if (parent instanceof GrCatchClause) {
      return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, getResolveScope()); 
    }

    String argumentName = getElementToCompare();
    GrClosableBlock closure = findClosureWithArgument(parent);

    return findClosureParameterType(closure, argumentName, factory, this);
  }

  @Nullable
  public static PsiType findClosureParameterType(GrClosableBlock closure,
                                                 String argumentName,
                                                 PsiElementFactory factory,
                                                 PsiElement context) {
    if (closure != null && closure.getParent() instanceof GrMethodCallExpression) {
      GrMethodCallExpression methodCall = (GrMethodCallExpression)closure.getParent();
      String methodName = findMethodName(methodCall);
      //final GrExpression invokedExpression = methodCall.getInvokedExpression();
      //PsiType type = findQualifierType(methodCall);

      GrExpression expression = methodCall.getInvokedExpression();
      if (!(expression instanceof GrReferenceExpression)) return null;

      GrExpression qualifier = ((GrReferenceExpression)expression).getQualifierExpression();
      if (qualifier == null) return null;
      PsiType type = qualifier.getType();

      GrParameter[] params = closure.getParameters();
      if (type == null) {
        return null;
      }

      if ("each".equals(methodName) ||
          "every".equals(methodName) ||
          "collect".equals(methodName) ||
          "find".equals(methodName) ||
          "findAll".equals(methodName) ||
          "findIndexOf".equals(methodName)) {
        PsiType res = findTypeForCollection(qualifier, factory, context);
        if (closure.getParameters().length <= 1 && res != null) {
          return res;
        }

        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          if (closure.getParameters().length <= 1) {
            return getEntryForMap(type, factory, context);
          }
          if (closure.getParameters().length == 2) {
            if (argumentName.equals(params[0].getName())) {
              return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
            }
            return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
          }
        }
      }
      else if ("with".equals(methodName) && closure.getParameters().length <= 1) {
        return type;
      }
      else if ("eachWithIndex".equals(methodName)) {
        PsiType res = findTypeForCollection(qualifier, factory, context);
        if (closure.getParameters().length == 2 && res != null) {
          if (argumentName.equals(params[0].getName())) {
            return res;
          }
          return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, context);
        }
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          if (params.length == 2) {
            if (argumentName.equals(params[0].getName())) {
              return getEntryForMap(type, factory, context);
            }
            return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, context);
          }
          if (params.length == 3) {
            if (argumentName.equals(params[0].getName())) {
              return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
            }
            if (argumentName.equals(params[1].getName())) {
              return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
            }
            return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, context);
          }
        }
      }
      else if ("inject".equals(methodName) && params.length == 2) {
        if (argumentName.equals(params[0].getName())) {
          return factory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, context);
        }

        PsiType res = findTypeForCollection(qualifier, factory, context);
        if (res != null) {
          return res;
        }
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          return getEntryForMap(type, factory, context);
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiType getEntryForMap(PsiType map, PsiElementFactory factory, PsiElement context) {
    PsiType key = PsiUtil.substituteTypeParameter(map, CommonClassNames.JAVA_UTIL_MAP, 0, true);
    PsiType value = PsiUtil.substituteTypeParameter(map, CommonClassNames.JAVA_UTIL_MAP, 1, true);
    if (key != null && value != null) {
      return factory.createTypeFromText("java.util.Map.Entry<" + key.getCanonicalText() + ", " + value.getCanonicalText() + ">", context);
    }
    return null;
  }

  @Nullable
  private static PsiType findTypeForCollection(GrExpression qualifier, PsiElementFactory factory, PsiElement context) {
    PsiType iterType = qualifier.getType();
    if (iterType == null) return null;
    if (iterType instanceof PsiArrayType) {
      return ((PsiArrayType)iterType).getComponentType();
    }
    if (iterType instanceof GrTupleType) {
      PsiType[] types = ((GrTupleType)iterType).getParameters();
      return types.length == 1 ? types[0] : null;
    }

    if (factory.createTypeFromText("groovy.lang.IntRange", context).isAssignableFrom(iterType)) {
      return factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, context);
    }
    if (factory.createTypeFromText("groovy.lang.ObjectRange", context).isAssignableFrom(iterType)) {
      PsiElement element = qualifier;
      element = removeBrackets(element);
      if (element instanceof GrReferenceExpression) {
        GrReferenceExpression ref = (GrReferenceExpression)element;
        element = removeBrackets(ref.resolve());
      }
      if (element instanceof GrRangeExpression) {
        return getRangeElementType((GrRangeExpression)element);
      }
      return null;
    }

    PsiType res = PsiUtil.extractIterableTypeParameter(iterType, true);
    if (res != null) {
      return res;
    }

    if (iterType.equalsToText(CommonClassNames.JAVA_LANG_STRING) || iterType.equalsToText("java.io.File")) {
      return factory.createTypeFromText(CommonClassNames.JAVA_LANG_STRING, context);
    }
    return null;
  }

  private static PsiElement removeBrackets(PsiElement element) {
    while (element instanceof GrParenthesizedExpression) {
      element = ((GrParenthesizedExpression)element).getOperand();
    }
    return element;
  }

  @Nullable
  private static PsiType getRangeElementType(GrRangeExpression range) {
    GrExpression left = range.getLeftOperand();
    GrExpression right = range.getRightOperand();
    if (right != null) {
      final PsiType leftType = left.getType();
      final PsiType rightType = right.getType();
      if (leftType != null && rightType != null) {
        return TypesUtil.getLeastUpperBound(leftType, rightType, range.getManager());
      }
    }
    return null;
  }

  @Nullable
  private static String findMethodName(@NotNull GrMethodCallExpression methodCall) {
    GrExpression expression = methodCall.getInvokedExpression();
    if (expression instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)expression).getReferenceName();
    }
    return null;
  }

  @Nullable
  private static GrClosableBlock findClosureWithArgument(@NotNull PsiElement parent) {
    if (parent instanceof GrParameterList) {
      GrParameterList list = (GrParameterList)parent;
      if (list.getParent() instanceof GrClosableBlock) {
        return (GrClosableBlock)list.getParent();
      }
    }
    return null;
  }

  @NotNull
  public PsiType getType() {
    /*PsiType type = getTypeGroovy();
    if (type == null) type = super.getType();*/
    PsiType type = super.getType();
    if (isVarArgs()) {
      return new PsiEllipsisType(type);
    }
    else if (isMainMethodFirstUntypedParameter()) {
      PsiClassType stringType =
        JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeByFQClassName("java.lang.String", getResolveScope());
      return stringType.createArrayType();
    }
    else {
      return type;
    }
  }

  private boolean isMainMethodFirstUntypedParameter() {
    if (getTypeElementGroovy() != null) return false;

    if (getParent() instanceof GrParameterList) {
      GrParameterList parameterList = (GrParameterList)getParent();
      GrParameter[] params = parameterList.getParameters();
      if (params.length != 1 || this != params[0]) return false;

      if (parameterList.getParent() instanceof GrMethod) {
        GrMethod method = (GrMethod)parameterList.getParent();
        return PsiImplUtil.isMainMethod(method);
      }
    }
    return false;
  }

  public void setType(@Nullable PsiType type) {
    throw new RuntimeException("NIY");
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  @Nullable
  public GrExpression getDefaultInitializer() {
    return findChildByClass(GrExpression.class);
  }

  public boolean isOptional() {
    return getDefaultInitializer() != null;
  }

  @NotNull
  public SearchScope getUseScope() {
    if (!isPhysical()) {
      final PsiFile file = getContainingFile();
      final PsiElement context = file.getContext();
      if (context != null) return new LocalSearchScope(context);
      return super.getUseScope();
    }

    final PsiElement scope = getDeclarationScope();
    if (scope instanceof GrDocCommentOwner) {
      GrDocCommentOwner owner = (GrDocCommentOwner)scope;
      final GrDocComment comment = owner.getDocComment();
      if (comment != null) {
        return new LocalSearchScope(new PsiElement[]{scope, comment});
      }
    }

    return new LocalSearchScope(scope);
  }

  @NotNull
  public String getName() {
    return getNameIdentifierGroovy().getText();
  }

  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Nullable
  public GrModifierList getModifierList() {
    return findChildByClass(GrModifierList.class);
  }

  @NotNull
  public PsiElement getDeclarationScope() {
    final GrParametersOwner owner = PsiTreeUtil.getParentOfType(this, GrParametersOwner.class);
    assert owner != null;
    if (owner instanceof GrForInClause) return owner.getParent();
    return owner;
  }

  public boolean isVarArgs() {
    PsiElement dots = findChildByType(GroovyTokenTypes.mTRIPLE_DOT);
    return dots != null;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiType getDeclaredType() {
    PsiType type = super.getDeclaredType();
    if (type == null) {
      type = getTypeGroovy();
    }
    return type;
  }
}
