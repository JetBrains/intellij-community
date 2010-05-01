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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;

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

    if (grInfo.isChangeName()) {
      method.setName(grInfo.getNewName());
    }
    
    if (grInfo.isVisibilityChanged()) {
      method.getModifierList().setModifierProperty(grInfo.getVisibilityModifier(), true);
    }

    if (grInfo.isParameterSetOrOrderChanged()) {
      JavaParameterInfo[] newParameters = grInfo.getNewParameters();
      GrParameterList parameterList = method.getParameterList();
      GrParameter[] oldParameters = parameterList.getParameters();


      PsiElement anchor = null;
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

      for (JavaParameterInfo newParameter : newParameters) {
        int index = newParameter.getOldIndex();
        if (index < 0) {
          GrParameter grParameter =
            factory.createParameter(newParameter.getName(), newParameter.getTypeText(), getInitializer(newParameter), parameterList);
          anchor = parameterList.addAfter(grParameter, anchor);
        }
        else {
          GrParameter grParameter = oldParameters[index];
          anchor = parameterList.addAfter(grParameter, anchor);
        }
      }

      for (GrParameter oldParameter : oldParameters) {
        oldParameter.delete();
      }
      CodeStyleManager.getInstance(parameterList.getProject()).reformat(parameterList);
    }
    return true;
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

    if (usageInfo instanceof GrMethodCallUsageInfo) {
      processMethodUsage(element, ((JavaChangeInfo)changeInfo), ((GrMethodCallUsageInfo)usageInfo).isToChangeArguments(),
                         ((GrMethodCallUsageInfo)usageInfo).isToCatchExceptions(),
                         ((JavaChangeInfo)changeInfo).getMethod(), ((GrMethodCallUsageInfo)usageInfo).getMapToArguments());
      return true;
    }
    return false;
  }

  private static void processMethodUsage(PsiElement element,
                                         JavaChangeInfo changeInfo,
                                         boolean toChangeArguments,
                                         boolean toCatchExceptions,
                                         PsiMethod method, GrClosureSignatureUtil.ArgInfo[] map) {
    if (map == null) return;
    if (changeInfo.isNameChanged()) {
      if (element instanceof GrReferenceElement) {
        element = ((GrReferenceElement)element).bindToElement(method);
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
              PsiElement curArg = getNextArg(anchor, argumentList);
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
          anchor = argumentList.addBefore(fromText, getNextArg(anchor, argumentList));
        }
      }

      CodeStyleManager.getInstance(argumentList.getProject()).reformat(argumentList);
    }
  }

  @Nullable
  private static PsiElement getNextArg(PsiElement anchor, GrArgumentList argumentList) {
    if (anchor == null) {
      anchor = argumentList.getFirstChild();
      if (anchor == null) return null;
      if (anchor instanceof GrExpression) return anchor;
    }
    return PsiTreeUtil.getNextSiblingOfType(anchor, GrExpression.class);
  }

  private static boolean wasParameterOptional(JavaParameterInfo parameterInfo) {
    return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).wasOptional();
  }

  private static boolean isParameterOptional(JavaParameterInfo parameterInfo) {
    return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).isOptional();
  }
}
