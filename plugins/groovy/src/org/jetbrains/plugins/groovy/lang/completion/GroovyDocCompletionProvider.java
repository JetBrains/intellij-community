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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.handlers.GroovyMethodSignatureInsertHandler;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author Max Medvedev
 */
public class GroovyDocCompletionProvider extends CompletionProvider<CompletionParameters> {
  public static void register(CompletionContributor contributor) {
    final GroovyDocCompletionProvider provider = new GroovyDocCompletionProvider();
    contributor.extend(CompletionType.BASIC, PsiJavaPatterns.psiElement().inside(GrDocTagValueToken.class), provider);
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    GrDocMemberReference reference = PsiTreeUtil.getParentOfType(position, GrDocMemberReference.class);
    if (reference == null) return;

    GrDocReferenceElement holder = reference.getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    }
    else {
      resolved = PsiUtil.getContextClass(reference);
    }
    if (resolved instanceof PsiClass) {
      ResolverProcessor propertyProcessor = CompletionProcessor.createPropertyCompletionProcessor(reference);
      resolved.processDeclarations(propertyProcessor, ResolveState.initial(), null, reference);
      PsiElement[] propertyCandidates = ResolveUtil.mapToElements(propertyProcessor.getCandidates());
      ResolverProcessor methodProcessor = CompletionProcessor.createPropertyCompletionProcessor(reference);

      resolved.processDeclarations(methodProcessor, ResolveState.initial(), null, reference);

      PsiElement[] methodCandidates = ResolveUtil.mapToElements(methodProcessor.getCandidates());

      PsiElement[] elements = ArrayUtil.mergeArrays(propertyCandidates, methodCandidates);

      for (PsiElement psiElement : elements) {
        LookupElement element = GroovyCompletionUtil.createLookupElement((PsiNamedElement)psiElement);
        if (psiElement instanceof PsiMethod) {
          element = ((LookupElementBuilder)element).withInsertHandler(new GroovyMethodSignatureInsertHandler());
        }
        result.addElement(element);
      }
    }
  }
}
