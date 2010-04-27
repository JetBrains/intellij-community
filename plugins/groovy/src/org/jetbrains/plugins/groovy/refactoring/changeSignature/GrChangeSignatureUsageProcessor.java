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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
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
    return false;
  }

  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages) {
    if (!(changeInfo instanceof JavaChangeInfo)) return false;
    PsiElement element = usageInfo.getElement();
    if (element == null) return false;

    if (usageInfo instanceof GrMethodCallUsageInfo) {
      processMethodUsage(element, ((JavaChangeInfo)changeInfo), ((GrMethodCallUsageInfo)usageInfo).isToChangeArguments(),
                         ((GrMethodCallUsageInfo)usageInfo).isToCatchExceptions(), ((JavaChangeInfo)changeInfo).getMethod());
      GrCall call = (GrCall)element;
      GrArgumentList argumentList = call.getArgumentList();
      return true;
    }
    return false;
  }

  private static void processMethodUsage(PsiElement element,
                                         JavaChangeInfo changeInfo,
                                         boolean toChangeArguments,
                                         boolean toCatchExceptions,
                                         PsiMethod method) {
    if (changeInfo.isNameChanged()) {
      if (element instanceof GrCodeReferenceElement) {
        ((GrCodeReferenceElement)element).bindToElement(method);
      }
    }
    if (toChangeArguments) {
      JavaParameterInfo[] parameters = changeInfo.getNewParameters();
      GrArgumentList argumentList = ((GrCall)element.getParent()).getArgumentList();
      if (argumentList == null) return;
      List<PsiElement>[] map = GrClosureSignatureUtil
        .mapParametersToArguments(GrClosureSignatureUtil.createSignature(method), argumentList, element.getManager(),
                                  method.getResolveScope());

      if (map == null) return;
      PsiElement child = argumentList.getFirstChild();
      if (child != null) {
        argumentList.deleteChildRange(child, argumentList.getLastChild());
      }

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
      for (int i = 0; i < parameters.length; i++) {
        JavaParameterInfo parameter = parameters[i];
        int index = parameter.getOldIndex();
        if (index >= 0) {
          List<PsiElement> arguments = map[index];
          if (arguments.size() == 1) {
            PsiElement arg = arguments.iterator().next();
            if (i > 0 && arg instanceof GrNamedArgument) {
              arg = factory.createExpressionFromText("[" + arg.getText() + "]");
            }
            argumentList.add(arg);
          }
          else if (arguments.size() > 1) {
            if (i == 0 && arguments.iterator().next() instanceof GrNamedArgument ||
                i == parameters.length - 1 && parameter.isVarargType()) {
              for (PsiElement argument : arguments) {
                argumentList.add(argument);
              }
            }
            else {
              StringBuilder argText = new StringBuilder();
              argText.append("[");
              for (PsiElement argument : arguments) {
                argText.append(argument.getText()).append(", ");
              }
              argText.replace(argText.length() - 2, argText.length(), "]");
              argumentList.add(factory.createExpressionFromText(argText.toString()));
            }
          }
          else if (i < parameters.length - 1) {
            argumentList.add(factory.createExpressionFromText("[]"));
          }
        }
      }
    }
  }
}
