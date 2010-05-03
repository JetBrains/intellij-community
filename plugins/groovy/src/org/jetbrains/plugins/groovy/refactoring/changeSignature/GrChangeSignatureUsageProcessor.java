/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureUsageProcessor");

  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof JavaChangeInfo) {
      return new GrChageSignatureUsageSearcher((JavaChangeInfo)info).findUsages();
    }
    return UsageInfo.EMPTY_ARRAY;
  }

  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    if (info instanceof JavaChangeInfo) {
      return new GrChangeSignatureConflictSearcher((JavaChangeInfo)info).findConflicts(refUsages);
    }
    else {
      return new MultiMap<PsiElement, String>();
    }
  }

  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    if (!(changeInfo instanceof GrChangeInfoImpl)) return false;

    GrChangeInfoImpl grInfo = (GrChangeInfoImpl)changeInfo;
    GrMethod method = grInfo.getMethod();

    return processPrimaryMethodInner(grInfo, method, null);
  }

  private static boolean processPrimaryMethodInner(JavaChangeInfo changeInfo, GrMethod method, PsiMethod baseMethod) {
    if (changeInfo.isNameChanged()) {
      String newName = baseMethod == null ? changeInfo.getNewName() :
                       RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());
      if (newName != null && !newName.equals(method.getName())) {
        method.setName(changeInfo.getNewName());
      }
    }

    if (changeInfo.isVisibilityChanged()) {
      method.getModifierList().setModifierProperty(changeInfo.getNewVisibility(), true);
    }

    if (changeInfo.isReturnTypeChanged()) {
      CanonicalTypes.Type newReturnType = changeInfo.getNewReturnType();
      GrTypeElement element = method.getReturnTypeElementGroovy();
      if (newReturnType == null) {
        if (element != null) {
          element.delete();
          GrModifierList modifierList = method.getModifierList();
          if (modifierList.getModifiers().length == 0) {
            modifierList.setModifierProperty(GrModifier.DEF, true);
          }
        }
      } else {
        PsiType type = newReturnType.getType(method.getParameterList(), method.getManager());
        method.setReturnType(type);
      }
    }

    JavaParameterInfo[] newParameters = changeInfo.getNewParameters();
    final GrParameterList parameterList = method.getParameterList();
    GrParameter[] oldParameters = parameterList.getParameters();

    Set<GrParameter> toRemove = new HashSet<GrParameter>(oldParameters.length);
    toRemove.addAll(Arrays.asList(oldParameters));

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());
    GrParameter anchor = null;
    for (JavaParameterInfo newParameter : newParameters) {
      /*
      int index = newParameter.getOldIndex();
      if (index < 0) {
      */
        String typeText;
        if (newParameter instanceof GrParameterInfo && ((GrParameterInfo)newParameter).hasNoType()) {
          typeText = null;
        }
        else {
          typeText = newParameter.getTypeText();
        }
        GrParameter grParameter =
          factory.createParameter(newParameter.getName(), typeText, getInitializer(newParameter), parameterList);
        anchor = (GrParameter)parameterList.addAfter(grParameter, anchor);
/*      }
      else {
        GrParameter grParameter = oldParameters[index];
        if (grParameter != getNextOfType(parameterList, anchor, GrParameter.class)) {
          anchor = (GrParameter)parameterList.addAfter(grParameter, anchor);
        }
        else {
          anchor = grParameter;
          toRemove.remove(grParameter);
        }
        if (anchor.getName() != newParameter.getName()) {
          anchor.setName(newParameter.getName());
        }
      }*/
    }

    for (GrParameter oldParameter : toRemove) {
      oldParameter.delete();
    }
    CodeStyleManager.getInstance(parameterList.getProject()).reformat(parameterList);

    return true;
  }

  @Nullable
  private static <Type extends PsiElement, List extends PsiElement> Type getNextOfType(List parameterList, PsiElement current, Class<Type> type) {
    return current != null ? PsiTreeUtil.getNextSiblingOfType(current, type) : PsiTreeUtil.getChildOfType(parameterList, type);
  }

  @Nullable
  private static String getInitializer(JavaParameterInfo newParameter) {
    if (newParameter instanceof GrParameterInfo) return ((GrParameterInfo)newParameter).getDefaultInitializer();
    return null;
  }

  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages) {
    if (!(changeInfo instanceof JavaChangeInfo)) return false;

    PsiElement element = usageInfo.getElement();
    if (element == null) return false;
    if (!GroovyFileType.GROOVY_LANGUAGE.equals(element.getLanguage())) return false;

    if (beforeMethodChange) {
      if (usageInfo instanceof OverriderUsageInfo) {
        processPrimaryMethodInner(((JavaChangeInfo)changeInfo), (GrMethod)((OverriderUsageInfo)usageInfo).getElement(),
                                  ((OverriderUsageInfo)usageInfo).getBaseMethod());
      }
    }
    else {
      if (usageInfo instanceof GrMethodCallUsageInfo) {
        processMethodUsage(element, ((JavaChangeInfo)changeInfo), ((GrMethodCallUsageInfo)usageInfo).isToChangeArguments(),
                           ((GrMethodCallUsageInfo)usageInfo).isToCatchExceptions(),
                           ((GrMethodCallUsageInfo)usageInfo).getMapToArguments());
        return true;
      }
      else if (usageInfo instanceof DefaultConstructorImplicitUsageInfo) {
        processConstructor(
          (GrMethod)((DefaultConstructorImplicitUsageInfo)usageInfo).getConstructor(),
          (JavaChangeInfo)changeInfo);
        return true;
      }
      else if (usageInfo instanceof NoConstructorClassUsageInfo) {
        processClassUsage((GrTypeDefinition)((NoConstructorClassUsageInfo)usageInfo).getPsiClass(), ((JavaChangeInfo)changeInfo));
        return true;
      }
      else if (usageInfo instanceof ChangeSignatureParameterUsageInfo) {
        String newName = ((ChangeSignatureParameterUsageInfo)usageInfo).newParameterName;
        String oldName = ((ChangeSignatureParameterUsageInfo)usageInfo).oldParameterName;
        processParameterUsage((PsiReference)element, oldName, newName);
        return true;
      }
      else {
        PsiReference ref = element.getReference();
        if (ref != null && changeInfo.getMethod() != null) {
          ref.bindToElement(changeInfo.getMethod());
          return true;
        }
      }
    }
    return false;
  }

  private static void processParameterUsage(PsiReference ref, String oldName, String newName)
    throws IncorrectOperationException {
    ref.handleElementRename(newName);
  }

  private static void processClassUsage(GrTypeDefinition psiClass, JavaChangeInfo changeInfo) {
    String name = psiClass.getName();

    GrConstructor constructor = ((GrConstructor)GroovyPsiElementFactory.getInstance(psiClass.getProject())
      .createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}"));

    GrModifierList list = constructor.getModifierList();
    if (psiClass.hasModifierProperty(GrModifier.PRIVATE)) list.setModifierProperty(GrModifier.PRIVATE, true);
    if (psiClass.hasModifierProperty(GrModifier.PROTECTED)) list.setModifierProperty(GrModifier.PROTECTED, true);
    if (!list.hasExplicitVisibilityModifiers()) {
      list.setModifierProperty(GrModifier.DEF, true);
    }

    constructor = psiClass.addMemberDeclaration(constructor, null);

    processConstructor(constructor, changeInfo);
  }

  private static void processConstructor(GrMethod constructor, JavaChangeInfo changeInfo) {
    GrOpenBlock block = constructor.getBlock();
    GrConstructorInvocation invocation =
      GroovyPsiElementFactory.getInstance(constructor.getProject()).createConstructorInvocation("super()");
    invocation = (GrConstructorInvocation)block.addStatementBefore(invocation, getFirstStatement(block));
    processMethodUsage(invocation.getThisOrSuperKeyword(), changeInfo, true, true, GrClosureSignatureUtil.ArgInfo.EMPTY_ARRAY);
  }

  @Nullable
  private static GrStatement getFirstStatement(GrCodeBlock block) {
    GrStatement[] statements = block.getStatements();
    if (statements.length == 0) return null;
    return statements[0];
  }

  private static void processMethodUsage(PsiElement element,
                                         JavaChangeInfo changeInfo,
                                         boolean toChangeArguments,
                                         boolean toCatchExceptions,
                                         GrClosureSignatureUtil.ArgInfo[] map) {
    if (map == null) return;
    if (changeInfo.isNameChanged()) {
      if (element instanceof GrReferenceElement) {
        element = ((GrReferenceElement)element).handleElementRename(changeInfo.getNewName());
      }
    }
    if (toChangeArguments) {
      JavaParameterInfo[] parameters = changeInfo.getNewParameters();
      GrArgumentList argumentList = ((GrCall)element.getParent()).getArgumentList();
      if (argumentList == null) return;
      Set<PsiElement> argsToDelete = new HashSet<PsiElement>(map.length * 2);
      for (GrClosureSignatureUtil.ArgInfo argInfo : map) {
        argsToDelete.addAll(argInfo.args);
      }

      for (JavaParameterInfo parameter : parameters) {
        int index = parameter.getOldIndex();
        if (index >= 0) {
          argsToDelete.removeAll(map[index].args);
        }
      }

      for (PsiElement arg : argsToDelete) {
        arg.delete();
      }

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());

      boolean skipOptionals = false;
      PsiElement anchor = null;
      for (int i = 0; i < parameters.length; i++) {
        JavaParameterInfo parameter = parameters[i];
        int index = parameter.getOldIndex();
        if (index >= 0) {
          GrClosureSignatureUtil.ArgInfo argInfo = map[index];
          List<PsiElement> arguments = argInfo.args;
          if (argInfo.isMultiArg) { //arguments for Map and varArg
            if ((i != 0 || !(arguments.size() > 0 && arguments.iterator().next() instanceof GrNamedArgument)) &&
                (i != parameters.length - 1 || !parameter.isVarargType())) {
              StringBuilder argText = new StringBuilder();
              argText.append("[");
              for (PsiElement argument : arguments) {
                argText.append(argument.getText()).append(", ");
                argument.delete();
              }
              argText.replace(argText.length() - 2, argText.length(), "]");
              if (!(arguments.size() > 0 && arguments.iterator().next() instanceof GrNamedArgument)) {
                argText.append(" as ").append(parameter.getTypeText());
              }
              anchor = argumentList.addAfter(factory.createExpressionFromText(argText.toString()), anchor);
            }
          }
          else {  //arguments for simple parameters
            if (arguments.size() == 1) { //arg exists
              PsiElement arg = arguments.iterator().next();
              PsiElement curArg = getNextOfType(argumentList, anchor, GrExpression.class);
              if (curArg == arg) {
                anchor = arg;
              }
              else {
                anchor = argumentList.addBefore(arg, anchor);
              }
            }
            else { //arg is skipped. Parameter is optional
              skipOptionals = true;
            }
          }
        }
        else {
          if (skipOptionals && isParameterOptional(parameter)) continue;
          GrExpression fromText = null;
          try {
            fromText = factory.createExpressionFromText(parameter.getDefaultValue());
          }
          catch (IncorrectOperationException e) {
            LOG.error(e.getMessage());
          }
          anchor = argumentList.addBefore(fromText, getNextOfType(argumentList, anchor, GrExpression.class));
        }
      }

      CodeStyleManager.getInstance(argumentList.getProject()).reformat(argumentList);
    }
  }

  private static boolean wasParameterOptional(JavaParameterInfo parameterInfo) {
    return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).wasOptional();
  }

  private static boolean isParameterOptional(JavaParameterInfo parameterInfo) {
    return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).isOptional();
  }
}
