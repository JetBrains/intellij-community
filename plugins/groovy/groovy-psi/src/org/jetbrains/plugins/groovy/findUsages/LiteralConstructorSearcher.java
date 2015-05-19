/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;

/**
* @author peter
*/
public class LiteralConstructorSearcher {
  private final PsiMethod myConstructor;
  private final Processor<PsiReference> myConsumer;
  private final boolean myIncludeOverloads;

  public LiteralConstructorSearcher(PsiMethod constructor, Processor<PsiReference> consumer, boolean includeOverloads) {
    myConstructor = constructor;
    myConsumer = consumer;
    myIncludeOverloads = includeOverloads;
  }

  public boolean processLiteral(GrListOrMap literal) {
    final PsiReference reference = literal.getReference();
    if (reference instanceof LiteralConstructorReference) {
      if (isCorrectReference((LiteralConstructorReference)reference) && !myConsumer.process(reference)) {
        return false;
      }
    }
    return true;
  }

  private boolean isCorrectReference(LiteralConstructorReference reference) {
    if (reference.isReferenceTo(myConstructor)) {
      return true;
    }

    if (!myIncludeOverloads) {
      return false;
    }

    PsiClassType constructedClassType = reference.getConstructedClassType();
    if (constructedClassType == null) return false;

    final PsiClass psiClass = constructedClassType.resolve();
    return myConstructor.getManager().areElementsEquivalent(myConstructor.getContainingClass(), psiClass);
  }
}
