// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface GrGdkMethod extends PsiMethod, PsiMirrorElement {

  @NotNull
  PsiType getReceiverType();

  @NotNull
  PsiMethod getStaticMethod();
}
