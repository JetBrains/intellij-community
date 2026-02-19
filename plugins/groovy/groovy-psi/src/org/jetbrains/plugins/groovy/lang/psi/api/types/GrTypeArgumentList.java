// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.types;

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Dmitry.Krasilschikov
 */
public interface GrTypeArgumentList extends GroovyPsiElement {

  int getTypeArgumentCount();

  GrTypeElement[] getTypeArgumentElements();

  PsiType[] getTypeArguments();

  boolean isDiamond();
}
