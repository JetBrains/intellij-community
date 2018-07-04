// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_CLASS;

/**
 * @author ven
 */
public class ClassResolverProcessor extends ResolverProcessorImpl {
  public ClassResolverProcessor(String refName, PsiElement place) {
    super(refName, RESOLVE_KINDS_CLASS, place, PsiType.EMPTY_ARRAY);
  }
}
