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
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class JavaSafeDeleteDelegateForGroovy implements JavaSafeDeleteDelegate {
  @Override
  public void createUsageInfoForParameter(PsiReference reference,
                                          List<UsageInfo> usages,
                                          final PsiParameter parameter,
                                          final PsiMethod method) {
    int index = method.getParameterList().getParameterIndex(parameter);
    final PsiElement element = reference.getElement();
    GrCall call = null;
    if (element instanceof GrCall) {
      call = (GrCall)element;
    }
    else if (element.getParent() instanceof GrCall) {
      call = (GrCall)element.getParent();
    }
    if (call != null) {
      GrClosureSignature signature = GrClosureSignatureUtil.createSignature(call);
      if (signature == null) return;//todo ???
      GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);
      if (argInfos == null) return;          //todo???

      for (PsiElement arg : argInfos[index].args) {
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(arg, parameter, true));
      }
    }
    else if (element instanceof GrDocMethodReference) {
      @NonNls final StringBuilder newText = new StringBuilder();
      newText.append("/** @see ");
      GrDocReferenceElement holder = ((GrDocMethodReference)element).getReferenceHolder();
      if (holder != null) {
        newText.append(holder.getText());
      }
      newText.append('#');
      newText.append(method.getName());
      newText.append('(');
      final List<PsiParameter> parameters = new ArrayList<>(Arrays.asList(method.getParameterList().getParameters()));
      parameters.remove(parameter);
      newText.append(StringUtil.join(parameters, psiParameter -> parameter.getType().getCanonicalText(), ","));
      newText.append(")*/");
      usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
        @Override
        public void deleteElement() throws IncorrectOperationException {
          ((GrDocMethodReference)element).bindToText(method.getProject(), newText.toString());
        }
      });
    }
  }
}
