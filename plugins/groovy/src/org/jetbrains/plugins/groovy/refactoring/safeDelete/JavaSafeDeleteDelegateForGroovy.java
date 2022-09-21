// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
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
  public void createUsageInfoForParameter(@NotNull PsiReference reference,
                                          @NotNull List<UsageInfo> usages,
                                          @NotNull PsiNamedElement parameter,
                                          int paramIdx, boolean isVararg) {
    final PsiElement element = reference.getElement();
    GrCall call = null;
    if (element instanceof GrCall) {
      call = (GrCall)element;
    }
    else if (element.getParent() instanceof GrCall) {
      call = (GrCall)element.getParent();
    }
    if (call != null) {
      GrSignature signature = GrClosureSignatureUtil.createSignature(call);
      if (signature == null) return;//todo ???
      GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);
      if (argInfos == null) return;          //todo???

      for (PsiElement arg : argInfos[paramIdx].args) {
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
      newText.append(((GrDocMethodReference)element).getReferenceName());
      newText.append('(');
      final List<GrDocMethodParameter> parameters = new ArrayList<>(Arrays.asList(((GrDocMethodReference)element).getParameterList().getParameters()));
      parameters.remove(paramIdx);
      newText.append(StringUtil.join(parameters, p -> p.getText(), ","));
      newText.append(")*/");
      usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
        @Override
        public void deleteElement() throws IncorrectOperationException {
          PsiElement e = getElement();
          if (e != null) {
            ((GrDocMethodReference)e).bindToText(e.getProject(), newText.toString());
          }
        }
      });
    }
  }
}
