// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;

public class LiteralConstructorSearcher {
  private final PsiMethod myConstructor;
  private final Processor<? super PsiReference> myConsumer;
  private final boolean myIncludeOverloads;

  public LiteralConstructorSearcher(PsiMethod constructor, Processor<? super PsiReference> consumer, boolean includeOverloads) {
    myConstructor = constructor;
    myConsumer = consumer;
    myIncludeOverloads = includeOverloads;
  }

  public boolean processLiteral(GrListOrMap literal) {
    GroovyConstructorReference reference = literal.getConstructorReference();
    if (reference != null) {
      if (isCorrectReference(reference) && !myConsumer.process(reference)) {
        return false;
      }
    }
    return true;
  }

  private boolean isCorrectReference(GroovyConstructorReference reference) {
    if (reference.isReferenceTo(myConstructor)) {
      return true;
    }

    if (!myIncludeOverloads) {
      return false;
    }
    GroovyResolveResult classResult = reference.resolveClass();
    if (classResult == null) {
      return false;
    }
    return myConstructor.getManager().areElementsEquivalent(myConstructor.getContainingClass(), classResult.getElement());
  }
}
