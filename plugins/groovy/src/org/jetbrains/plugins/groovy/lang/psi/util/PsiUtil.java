/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.util;

import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.List;
import java.util.ArrayList;

/**
 * @author ven
 */
public class PsiUtil {
  @Nullable
  public static String getQualifiedReferenceText(GrTypeOrPackageReferenceElement referenceElement) {
    StringBuilder builder = new StringBuilder();
    if (!appendName(referenceElement, builder)) return null;

    return builder.toString();
  }

  private static boolean appendName(GrTypeOrPackageReferenceElement referenceElement, StringBuilder builder) {
    String refName = referenceElement.getReferenceName();
    if (refName == null) return false;
    GrTypeOrPackageReferenceElement qualifier = referenceElement.getQualifier();
    if (qualifier != null) {
      appendName(qualifier, builder);
      builder.append(".");
    }

    builder.append(refName);
    return true;
  }

  public static boolean isLValue(GroovyPsiElement element) {
    if (element instanceof GrExpression) {
      PsiElement parent = element.getParent();
      return parent instanceof GrAssignmentExpression &&
          element.equals(((GrAssignmentExpression) parent).getLValue());
    }
    return false;
  }

  public static PsiType boxPrimitiveTypeAndEraseGenerics(PsiType result, PsiManager manager, GlobalSearchScope resolveScope) {
    if (result instanceof PsiPrimitiveType) {
      PsiPrimitiveType primitive = (PsiPrimitiveType) result;
      String boxedTypeName = primitive.getBoxedTypeName();
      if (boxedTypeName != null) {
        return manager.getElementFactory().createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return TypeConversionUtil.erasure(result);
  }

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes, PsiMethod method) {
    if (argumentTypes == null) return true;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > argumentTypes.length) return false;
    if (parameters.length == 0 && argumentTypes.length > 0) return false;

    for (int i = 0; i < argumentTypes.length; i++) {
      PsiType argType = argumentTypes[i];
      PsiType parameterTypeToCheck;
      if (i < parameters.length - 1) {
        parameterTypeToCheck = parameters[i].getType();
      } else {
        PsiType lastParameterType = parameters[parameters.length - 1].getType();
        if (lastParameterType instanceof PsiArrayType) {
          parameterTypeToCheck = ((PsiArrayType) lastParameterType).getComponentType();
        } else if (parameters.length == argumentTypes.length) {
            parameterTypeToCheck = lastParameterType;
          } else {
            return false;
          }
      }
      parameterTypeToCheck =
          boxPrimitiveTypeAndEraseGenerics(parameterTypeToCheck, method.getManager(), method.getResolveScope());
      if (!parameterTypeToCheck.isAssignableFrom(argType)) return false;
    }

    return true;
  }

  @Nullable
  public static PsiType[] getArgumentTypes(GroovyPsiElement place) {
    PsiElementFactory factory = place.getManager().getElementFactory();
    PsiElement parent = place.getParent();
    if (parent instanceof GrMethodCall) {
      List<PsiType> result = new ArrayList<PsiType>();
      GrMethodCall methodCall = (GrMethodCall) parent;
      GrNamedArgument[] namedArgs = methodCall.getNamedArguments();
      if (namedArgs.length > 0) {
        result.add(factory.createTypeByFQClassName("java.util.HashMap", place.getResolveScope()));
      }
      GrExpression[] expressions = methodCall.getExpressionArguments();
      for (GrExpression expression : expressions) {
        PsiType type = expression.getType();
        if (type == null) {
          result.add(PsiType.NULL);
        } else {
          result.add(type);
        }
      }
      return result.toArray(new PsiType[result.size()]);

    } else if (parent instanceof GrApplicationExpression) {
      GrExpression[] args = ((GrApplicationExpression) parent).getArguments();
      PsiType[] result = new PsiType[args.length];
      for (int i = 0; i < result.length; i++) {
        PsiType argType = args[i].getType();
        if (argType == null) {
          result[i] = PsiType.NULL;
        } else {
          result[i] = argType;
        }
      }

      return result;
    }

    return null;
  }
}
