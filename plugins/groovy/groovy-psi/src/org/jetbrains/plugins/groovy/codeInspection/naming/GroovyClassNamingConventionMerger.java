// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.codeInspection.naming.AbstractNamingConventionMerger;
import com.intellij.psi.PsiClass;

public class GroovyClassNamingConventionMerger extends AbstractNamingConventionMerger<PsiClass> {
  public GroovyClassNamingConventionMerger() {
    super(new NewGroovyClassNamingConventionInspection());
  }
}
