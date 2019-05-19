// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

public class GroovyPsiElementVisitor extends PsiElementVisitor {
  protected GroovyElementVisitor myGroovyElementVisitor;

  public GroovyPsiElementVisitor(GroovyElementVisitor groovyElementVisitor) {
    myGroovyElementVisitor = groovyElementVisitor;
  }

  @Override
  public void visitElement(PsiElement element) {
    if (element instanceof GroovyPsiElement) {
      ((GroovyPsiElement) element).accept(myGroovyElementVisitor);
    }
  }
}
