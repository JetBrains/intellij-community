/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;

/**
 * @author peter
 */
public class GppImplicitUsageProvider implements ImplicitUsageProvider {

  private static boolean isGppMetaMethod(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0 || !parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }
    
    if ("invokeUnresolvedMethod".equals(method.getName())) {
      return true;
    }
    if ("getUnresolvedProperty".equals(method.getName())) {
      return parameters.length == 1;
    }
    if ("setUnresolvedProperty".equals(method.getName())) {
      return parameters.length == 2;
    }

    return false;
  }
  
  @Override
  public boolean isImplicitUsage(PsiElement element) {
    return element instanceof PsiMethod && isGppMetaMethod((PsiMethod)element);
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return false;
  }
}
