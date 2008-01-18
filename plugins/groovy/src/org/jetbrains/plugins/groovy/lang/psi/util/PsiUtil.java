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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.grails.fileType.GspFileType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.AccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

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

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes, PsiMethod method, PsiSubstitutor substitutor, boolean isInUseCategory) {
    if (argumentTypes == null) return true;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (isInUseCategory && method.hasModifierProperty(PsiModifier.STATIC) && parameters.length > 0) {
      //do not check first parameter, it is 'this' inside categorized block
      parameters = ArrayUtil.remove(parameters, 0);
    }

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

  public static PsiType[] skipOptionalParametersAndSubstitute(int argNum, PsiParameter[] parameters, PsiSubstitutor substitutor) {
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
    } else if (parent instanceof GrConstructorInvocation || parent instanceof GrEnumConstant) {
      final GrArgumentList argList = (GrArgumentList) ((GrCall) parent).getArgumentList();
      assert argList != null;
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

  public static SearchScope restrictScopeToGroovyFiles(final Computable<SearchScope> originalScopeComputation) { //important to compute originalSearchScope in read action!
    return ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        final SearchScope originalScope = originalScopeComputation.compute();
        if (originalScope instanceof GlobalSearchScope) {
          return GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope) originalScope, GroovyFileType.GROOVY_FILE_TYPE, GspFileType.GSP_FILE_TYPE);
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
    return isSimplePropertyGetter(method, null);
  }

  //do not check return type
  public static boolean isSimplePropertyGetter(PsiMethod method, String propertyName) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    if (!methodName.startsWith("get") || methodName.length() <= "get".length() ||
        !Character.isUpperCase(methodName.charAt("get".length()))) return false;

    if (propertyName != null && !checkPropertyName(method, propertyName)) return false;

    return method.getParameterList().getParameters().length == 0;
  }

  private static boolean checkPropertyName(PsiMethod method, @NotNull String propertyName) {
    String methodName = method.getName();
    String accessorNamePart;
    if (method instanceof AccessorMethod) accessorNamePart = ((AccessorMethod) method).getProperty().getName();
    else {
      accessorNamePart = methodName.substring(3); //"set" or "get"
      if (Character.isLowerCase(accessorNamePart.charAt(0))) return false;
      accessorNamePart = StringUtil.decapitalize(accessorNamePart);
    }

    if (!propertyName.equals(accessorNamePart)) return false;
    return true;
  }

  public static boolean isSimplePropertySetter(PsiMethod method) {
    return isSimplePropertySetter(method, null);
  }

  public static boolean isSimplePropertySetter(PsiMethod method, String propertyName) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!methodName.startsWith("set") || methodName.length() <= "set".length() ||
        !Character.isUpperCase(methodName.charAt("set".length()))) return false;

    if (propertyName != null && !checkPropertyName(method, propertyName)) return false;

    return method.getParameterList().getParametersCount() == 1;
  }

  public static void shortenReferences(GroovyPsiElement element) {
    doShorten(element);
  }

  private static void doShorten(PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof GrCodeReferenceElement) {
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

  @Nullable
  public static GrTopLevelDefintion findPreviousTopLevelElementByThisElement(PsiElement element) {
    PsiElement parent = element.getParent();

    while (parent != null && !(parent instanceof GrTopLevelDefintion)) {
      parent = parent.getParent();
    }

    if (parent == null) return null;
    return ((GrTopLevelDefintion) parent);
  }

  public static String getPropertyNameByGetter(PsiMethod getterMethod) {
    @NonNls String methodName = getterMethod.getName();
    return methodName.startsWith("get") && methodName.length() > 3 ?
           decapitalize(methodName.substring(3)) :
           methodName;
  }

  public static String getPropertyNameBySetter(PsiMethod setterMethod) {
    @NonNls String methodName = setterMethod.getName();
    return methodName.startsWith("set") && methodName.length() > 3 ?
           decapitalize(methodName.substring(3)) :
           methodName;
  }

  private static String decapitalize(String s) {
    final char[] chars = s.toCharArray();
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }

  public static boolean isStaticsOK(PsiModifierListOwner owner, PsiElement place) {
    PsiElement run = place;
    if (owner.hasModifierProperty(PsiModifier.STATIC)) {
      if (place instanceof GrReferenceExpression && ((GrReferenceExpression) place).getQualifierExpression() == null) {
        while (run != null && run != owner) {
          if (run instanceof GrClosableBlock) return false;
          run = run.getContext();
        }
      }
    }

    return true;
  }

  public static boolean isAccessible(PsiElement place, PsiMember member) {

    if (place instanceof GrReferenceExpression && ((GrReferenceExpression) place).getQualifierExpression() == null) {
      if (member.getContainingClass() instanceof GroovyScriptClass) { //calling toplevel script membbers from the same script file
        return true;
      }
    }
    return com.intellij.psi.util.PsiUtil.isAccessible(member, place, null);
  }

  public static void reformatCode(final PsiElement element) {
    final TextRange textRange = element.getTextRange();
    try {
      CodeStyleManager.getInstance(element.getProject()).reformatText(element.getContainingFile(),
        textRange.getStartOffset(), textRange.getEndOffset());
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
