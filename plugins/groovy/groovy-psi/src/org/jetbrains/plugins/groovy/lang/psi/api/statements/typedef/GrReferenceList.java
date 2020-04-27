// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author ilyas
 */
public interface GrReferenceList extends GroovyPsiElement, PsiReferenceList {

  @Nullable
  PsiElement getKeyword();

  GrCodeReferenceElement @NotNull [] getReferenceElementsGroovy();

}
