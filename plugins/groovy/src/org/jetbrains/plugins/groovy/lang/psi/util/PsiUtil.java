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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class PsiUtil {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil");

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

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes, PsiMethod method, PsiSubstitutor substitutor) {
    if (argumentTypes == null) return true;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType[] parameterTypes = skipOptionalParametersAndSubstitute(argumentTypes.length, parameters, substitutor);
    if (parameterTypes.length - 1 > argumentTypes.length) return false; //one Map type might represent named arguments
    if (parameterTypes.length == 0 && argumentTypes.length > 0) return false;

    final PsiManager manager = method.getManager();
    final GlobalSearchScope scope = method.getResolveScope();

    if (parameterTypes.length - 1 == argumentTypes.length) {
      final PsiType firstType = parameterTypes[0];
      final PsiClassType mapType = getMapType(manager, scope);
      if (mapType.isAssignableFrom(firstType)) {
        final PsiType[] trimmed = new PsiType[parameterTypes.length - 1];
        System.arraycopy(parameterTypes, 1, trimmed, 0, trimmed.length);
        parameterTypes = trimmed;
      } else if (!method.isVarArgs()) return false;
    }

    for (int i = 0; i < argumentTypes.length; i++) {
      PsiType argType = argumentTypes[i];
      PsiType parameterTypeToCheck;
      if (i < parameterTypes.length - 1) {
        parameterTypeToCheck = parameterTypes[i];
      } else {
        PsiType lastParameterType = parameterTypes[parameterTypes.length - 1];
        if (lastParameterType instanceof PsiArrayType && !(argType instanceof PsiArrayType)) {
          parameterTypeToCheck = ((PsiArrayType) lastParameterType).getComponentType();
        } else if (parameterTypes.length == argumentTypes.length) {
          parameterTypeToCheck = lastParameterType;
        } else {
          return false;
        }
      }

      if (!TypesUtil.isAssignableByMethodCallConversion(parameterTypeToCheck, argType, manager, scope)) return false;
    }

    return true;
  }

  private static PsiType[] skipOptionalParametersAndSubstitute(int argNum, PsiParameter[] parameters, PsiSubstitutor substitutor) {
    int diff = parameters.length - argNum;
    List<PsiType> result = new ArrayList<PsiType>(argNum);
    for (PsiParameter parameter : parameters) {
      if (diff > 0 && parameter instanceof GrParameter && ((GrParameter) parameter).isOptional()) {
        diff--;
        continue;
      }

      result.add(substitutor.substitute(parameter.getType()));
    }

    return result.toArray(new PsiType[result.size()]);
  }

  public static PsiClassType getMapType(PsiManager manager, GlobalSearchScope scope) {
    return manager.getElementFactory().createTypeByFQClassName("java.util.Map", scope);
  }

  @Nullable
  public static GroovyPsiElement getArgumentsElement(PsiElement methodRef) {
    PsiElement parent = methodRef.getParent();
    if (parent instanceof GrMethodCallExpression) {
      return ((GrMethodCallExpression) parent).getArgumentList();
    } else if (parent instanceof GrApplicationStatement) {
      return ((GrApplicationStatement) parent).getArgumentList();
    } else if (parent instanceof GrNewExpression) {
      return ((GrNewExpression) parent).getArgumentList();
    }
    return null;
  }

  // Returns arguments types not including Map for named arguments
  @Nullable
  public static PsiType[] getArgumentTypes(PsiElement place, boolean forConstructor) {
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

    } else if (parent instanceof GrApplicationStatement) {
      GrExpression[] args = ((GrApplicationStatement) parent).getArguments();
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
    } else if (parent instanceof GrConstructorInvocation) {
      final GrArgumentList argList = ((GrConstructorInvocation) parent).getArgumentList();
      List<PsiType> result = new ArrayList<PsiType>();
      if (argList.getNamedArguments().length > 0) {
        result.add(factory.createTypeByFQClassName("java.util.HashMap", place.getResolveScope()));
      }

      GrExpression[] expressions = argList.getExpressionArguments();
      for (GrExpression expression : expressions) {
        PsiType type = getArgumentType(expression);
        if (type == null) {
          result.add(PsiType.NULL);
        } else {
          result.add(type);
        }
      }

      return result.toArray(new PsiType[result.size()]);
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

  public static PsiClass getJavaLangObject(PsiElement resolved, GlobalSearchScope scope) {
    return resolved.getManager().findClass("java.lang.Class", scope);
  }

  public static boolean isValidReferenceName(String text) {
    final GroovyLexer lexer = new GroovyLexer();
    lexer.start(text.toCharArray());
    return TokenSets.PROPERTY_NAMES.contains(lexer.getTokenType()) && lexer.getTokenEnd() == text.length();
  }

  public static boolean isSimplePropertyAccessor(PsiMethod method) {
    return isSimplePropertyGetter(method) || isSimplePropertySetter(method);
  }
  
  //do not check return type
  public static boolean isSimplePropertyGetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    if (methodName.startsWith("get") && methodName.length() > "get".length()) {
      if (Character.isLowerCase(methodName.charAt("get".length()))
          && (methodName.length() == "get".length() + 1 || Character.isLowerCase(methodName.charAt("get".length() + 1)))) {
        return false;
      }
      return method.getParameterList().getParametersCount() == 0;
    }

    return false;
  }

  public static boolean isSimplePropertySetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!(methodName.startsWith("set") && methodName.length() > "set".length())) return false;
    if (Character.isLowerCase(methodName.charAt("set".length()))) return false;

    return method.getParameterList().getParametersCount() == 1;
  }

  public static void shortenReferences(GroovyPsiElement element) {
    doShorten(element);
  }

  private static void doShorten(PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof GrReferenceElement) {
        final GrCodeReferenceElement ref = (GrCodeReferenceElement) child;
        if (ref.getQualifier() != null) {
          final PsiElement resolved = ref.resolve();
          if (resolved instanceof PsiClass) {
            ref.setQualifier(null);
            try {
              ref.bindToElement(resolved);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      doShorten(child);
      child = child.getNextSibling();
    }
  }
}
