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
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

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
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
