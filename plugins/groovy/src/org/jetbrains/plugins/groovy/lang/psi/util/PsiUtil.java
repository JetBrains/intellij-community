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

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class PsiUtil {
  @Nullable
  public static String getQualifiedReferenceText(GrCodeReferenceElement referenceElement) {
    StringBuilder builder = new StringBuilder();
    if (!appendName(referenceElement, builder)) return null;

    return builder.toString();
  }

  private static boolean appendName(GrCodeReferenceElement referenceElement, StringBuilder builder) {
    String refName = referenceElement.getReferenceName();
    if (refName == null) return false;
    GrCodeReferenceElement qualifier = referenceElement.getQualifier();
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

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes, PsiMethod method) {
    if (argumentTypes == null) return true;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length - 1 > argumentTypes.length) return false; //one Map type might represent named arguments
    if (parameters.length == 0 && argumentTypes.length > 0) return false;

    final PsiManager manager = method.getManager();
    final GlobalSearchScope scope = method.getResolveScope();

    if (parameters.length - 1 == argumentTypes.length) {
      final PsiType firstType = parameters[0].getType();
      final PsiClassType mapType = getMapType(manager, scope);
      if (mapType.isAssignableFrom(firstType)) {
        final PsiParameter[] trimmed = new PsiParameter[parameters.length - 1];
        System.arraycopy(parameters, 1, trimmed, 0, trimmed.length);
        parameters = trimmed;
      } else return false;
    }

    for (int i = 0; i < argumentTypes.length; i++) {
      PsiType argType = argumentTypes[i];
      PsiType parameterTypeToCheck;
      if (i < parameters.length - 1) {
        parameterTypeToCheck = parameters[i].getType();
      } else {
        PsiType lastParameterType = parameters[parameters.length - 1].getType();
        if (lastParameterType instanceof PsiArrayType && !(argType instanceof PsiArrayType)) {
          parameterTypeToCheck = ((PsiArrayType) lastParameterType).getComponentType();
        } else if (parameters.length == argumentTypes.length) {
          parameterTypeToCheck = lastParameterType;
        } else {
          return false;
        }
      }

      if (!TypesUtil.isAssignableByMethodCallConversion(parameterTypeToCheck, argType, manager, scope)) return false;
    }

    return true;
  }

  public static PsiClassType getMapType(PsiManager manager, GlobalSearchScope scope) {
    return manager.getElementFactory().createTypeByFQClassName("java.util.Map", scope);
  }

  @Nullable
  public static GroovyPsiElement getArgumentsElement(GroovyPsiElement methodRef) {
    PsiElement parent = methodRef.getParent();
    if (parent instanceof GrMethodCall) {
      return ((GrMethodCall) parent).getArgumentList();
    } else if (parent instanceof GrApplicationExpression) {
      return ((GrApplicationExpression) parent).getArgumentList();
    } else if (parent instanceof GrNewExpression) {
      return ((GrNewExpression) parent).getArgumentList();
    }
    return null;
  }

  // Returns arguments types not including Map for named arguments
  @Nullable
  public static PsiType[] getArgumentTypes(GroovyPsiElement place, boolean forConstructor) {
    PsiElementFactory factory = place.getManager().getElementFactory();
    PsiElement parent = place.getParent();
    if (parent instanceof GrCallExpression) {
      List<PsiType> result = new ArrayList<PsiType>();
      GrCallExpression call = (GrCallExpression) parent;

      if (!forConstructor) {
        GrNamedArgument[] namedArgs = call.getNamedArguments();
        if (namedArgs.length > 0) {
          result.add(factory.createTypeByFQClassName("java.util.HashMap", place.getResolveScope()));
        }
      }

      GrExpression[] expressions = call.getExpressionArguments();
      for (GrExpression expression : expressions) {
        PsiType type = getArgumentType(expression);
        if (type == null) {
          result.add(PsiType.NULL);
        } else {
          result.add(type);
        }
      }

      GrClosableBlock[] closures = call.getClosureArguments();
      for (GrClosableBlock closure : closures) {
        PsiType closureType = closure.getType();
        if (closureType != null) {
          result.add(closureType);
        }
      }

      return result.toArray(new PsiType[result.size()]);

    } else if (parent instanceof GrApplicationExpression) {
      GrExpression[] args = ((GrApplicationExpression) parent).getArguments();
      PsiType[] result = new PsiType[args.length];
      for (int i = 0; i < result.length; i++) {
        PsiType argType = getArgumentType(args[i]);
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

  private static PsiType getArgumentType(GrExpression expression) {
    if (expression instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression) expression).resolve();
      if (resolved instanceof PsiClass) {
        //this argument is passed as java.lang.Class
        return resolved.getManager().getElementFactory().createTypeByFQClassName("java.lang.Class", expression.getResolveScope());
      }
    }
    
    return expression.getType();
  }

  public static SearchScope restrictScopeToGroovyFiles(final SearchScope originalScope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        if (originalScope instanceof GlobalSearchScope) {
          return GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)originalScope, GroovyFileType.GROOVY_FILE_TYPE);
        }
        return originalScope;
      }
    });
  }
}
