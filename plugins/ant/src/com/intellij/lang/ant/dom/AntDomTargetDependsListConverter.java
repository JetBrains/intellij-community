// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomTargetDependsListConverter extends Converter<TargetResolver.Result> implements CustomReferenceConverter<TargetResolver.Result>{

  @Override
  public TargetResolver.Result fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
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
      refs = new ArrayList<>();
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

  @Override
  public @Nullable String toString(@Nullable TargetResolver.Result result, @NotNull ConvertContext context) {
    return result != null? result.getRefsString() : null;
  }

  @Override
  public PsiReference @NotNull [] createReferences(GenericDomValue<TargetResolver.Result> value, PsiElement element, ConvertContext context) {
    final XmlElement xmlElement = value.getXmlElement();
    if (!(xmlElement instanceof XmlAttribute)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue valueElement = ((XmlAttribute)xmlElement).getValueElement();
    if (valueElement == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final String refsString = value.getStringValue();
    if (refsString == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refs = new ArrayList<>();
    final AntDomTargetReference.ReferenceGroup group = new AntDomTargetReference.ReferenceGroup();
    final TextRange wholeStringRange = ElementManipulators.getValueTextRange(valueElement);
    final StringTokenizer tokenizer = new StringTokenizer(refsString, ",", false);
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
      refs.add(new AntDomTargetReference(element, TextRange.from(wholeStringRange.getStartOffset() + tokenStartOffset, ref.length()), group));
    }
    return refs.toArray(PsiReference.EMPTY_ARRAY);
  }

}
