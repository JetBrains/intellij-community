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
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.lang.ant.dom.AntDomReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.TagNameReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class AntUnresolvedRefsFixProvider extends UnresolvedReferenceQuickFixProvider<PsiReference> {

  public void registerFixes(@NotNull PsiReference ref, @NotNull QuickFixActionRegistrar registrar) {
    if (ref instanceof TagNameReference || ref instanceof AntDomReference) {
      registrar.register(new AntChangeContextFix());
    }
  }

  @NotNull
  public Class<PsiReference> getReferenceClass() {
    return PsiReference.class;
  }
}
