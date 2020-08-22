// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.naming.AbstractNamingConventionMerger;
import com.intellij.psi.PsiClass;

final class ClassNamingConventionMerger extends AbstractNamingConventionMerger<PsiClass> {
  ClassNamingConventionMerger() {
    super(new NewClassNamingConventionInspection());
  }
}
