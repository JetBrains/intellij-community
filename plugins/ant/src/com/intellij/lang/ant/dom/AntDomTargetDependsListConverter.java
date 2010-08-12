/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.references.PomService;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomTargetDependsListConverter extends Converter<TargetResolver.Result> implements CustomReferenceConverter<TargetResolver.Result>{
  
  public TargetResolver.Result fromString(@Nullable @NonNls String s, ConvertContext context) {
    final AntDomProject project = context.getInvocationElement().getParentOfType(AntDomProject.class, false);
    if (project == null) {
      return null;
    }
    final AntDomTarget contextTarget = context.getInvocationElement().getParentOfType(AntDomTarget.class, false);
    if (contextTarget == null) {
      return null;
    }
    final List<String> refs;
    if (s == null) {
      refs = Collections.emptyList();
    }
    else {
      refs = new ArrayList<String>();
      final StringTokenizer tokenizer = new StringTokenizer(s, ",", false);
      while (tokenizer.hasMoreTokens()) {
        final String ref = tokenizer.nextToken();
        refs.add(ref.trim());
      }
    }
    final TargetResolver.Result result = TargetResolver.resolve(project.getContextAntProject(), contextTarget, refs);
    result.setRefsString(s);
    return result;
  }

  @Nullable
  public String toString(@Nullable TargetResolver.Result result, ConvertContext context) {
    return result != null? result.getRefsString() : null;
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<TargetResolver.Result> value, PsiElement element, ConvertContext context) {
    final XmlElement xmlElement = value.getXmlElement();
    if (!(xmlElement instanceof XmlAttribute)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue valueElement = ((XmlAttribute)xmlElement).getValueElement();
    if (valueElement == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final TargetResolver.Result result = value.getValue();
    if (result == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refs = new ArrayList<PsiReference>();
    final StringTokenizer tokenizer = new StringTokenizer(result.getRefsString(), ",", false);
    final TextRange wholeStringRange = ElementManipulators.getValueTextRange(valueElement);
    final Set<String> existingRefs = new HashSet<String>();
    final AntDomTarget hostAntTarget = context.getInvocationElement().getParentOfType(AntDomTarget.class, false);
    if (hostAntTarget != null) {
      existingRefs.add(hostAntTarget.getName().getRawText()); // avoid setting dependencies onto itself
    }
    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      int tokenStartOffset = tokenizer.getCurrentPosition() - token.length();
      final String ref = token.trim();
      if (ref.length() != token.length()) {
        for (int idx = 0; idx < token.length(); idx++) {
          if (Character.isWhitespace(token.charAt(idx))) {
            tokenStartOffset++;
          }
          else {
            break;
          }
        }
      }
      final Pair<AntDomTarget,String> antTarget = result.getResolvedTarget(ref);
      final DomTarget domTarget = (antTarget != null && antTarget.getFirst() != null) ? DomTarget.getTarget(antTarget.getFirst()) : null;
      if (domTarget != null) {
        existingRefs.add(antTarget.getSecond());
      }
      refs.add(new PsiReferenceBase<PsiElement>(valueElement, TextRange.from(wholeStringRange.getStartOffset() + tokenStartOffset, ref.length()), true) {
        public PsiElement resolve() {
          return domTarget != null? PomService.convertToPsi(domTarget) : null;
        }
        @NotNull
        public Object[] getVariants() {
          final List<Object> variants = new ArrayList<Object>();
          for (Map.Entry<String, AntDomTarget> entry : result.getVariants().entrySet()) {
            final String targetEffectiveName = entry.getKey();
            if (!existingRefs.contains(targetEffectiveName)) {
              //final AntDomTarget target = entry.getValue();
              //final DomTarget _target = DomTarget.getTarget(target);
              //if (_target == null) {
              //  continue;
              //}
              //final PsiElement psi = PomService.convertToPsi(_target);
              //if (psi == null) {
              //  continue;
              //}
              final LookupElementBuilder builder = LookupElementBuilder.create(/*psi, */targetEffectiveName);
              final LookupElement element = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE.applyPolicy(builder);
              variants.add(element);
            }
          }
          return variants.toArray();
        }
      });
    }
    return refs.toArray(new PsiReference[refs.size()]);
  }

}
