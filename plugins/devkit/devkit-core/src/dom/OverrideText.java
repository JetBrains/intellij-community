// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.dataFlow.StringExpressionHelper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public interface OverrideText extends DomElement {

  @NotNull
  @Required
  @Convert(value = PlaceConverter.class, soft = true)
  GenericAttributeValue<PsiField> getPlace();

  @NotNull
  @Required(false)
  @Convert(value = PlaceConverter.class, soft = true)
  GenericAttributeValue<PsiField> getUseTextOfPlace();

  @NotNull
  @Required(false)
  GenericAttributeValue<String> getText();


  class PlaceConverter extends ResolvingConverter<PsiField> {

    @NonNls private static final String PLACES_CLASSNAME_SUFFIX = "Places";

    @Override
    public @Nullable PsiField fromString(@Nullable String s,
                                         ConvertContext context) {
      if (s == null) return null;
      CommonProcessors.FindProcessor<PsiField> resolve = new CommonProcessors.FindProcessor<>() {
        @Override
        protected boolean accept(PsiField field) {
          return s.equals(getPlaceName(field));
        }
      };

      processAllActionPlaces(context, resolve);
      return resolve.getFoundValue();
    }


    @Override
    public @Nullable String toString(@Nullable PsiField field,
                                     ConvertContext context) {
      return field != null ? field.getName() : null;
    }

    @Override
    public @Nullable
    LookupElement createLookupElement(PsiField field) {
      return LookupElementBuilder.create(field, Objects.requireNonNull(getPlaceName(field)))
        .withStrikeoutness(field.isDeprecated())
        .withTailText(" (" + field.getName() + ")")
        .withTypeText(Objects.requireNonNull(field.getContainingClass()).getName());
    }

    @NotNull
    @Override
    public Collection<PsiField> getVariants(ConvertContext context) {
      CommonProcessors.CollectProcessor<PsiField> collectProcessor = new CommonProcessors.CollectProcessor<>();
      processAllActionPlaces(context, collectProcessor);
      return collectProcessor.getResults();
    }

    private static void processAllActionPlaces(ConvertContext context, Processor<PsiField> fieldProcessor) {
      final GlobalSearchScope scope = context.getSearchScope();
      if (scope == null) return;

      AllClassesSearch.search(context.getSearchScope(), context.getProject(), s -> s.endsWith(PLACES_CLASSNAME_SUFFIX))
        .forEach(psiClass -> {
          return ContainerUtil.process(psiClass.getFields(), psiField -> {
            if (!psiField.hasModifierProperty(PsiModifier.PUBLIC)) return true;
            if (getPlaceName(psiField) == null) return true;

            return fieldProcessor.process(psiField);
          });
        });
    }

    @Nullable
    private static String getPlaceName(PsiField field) {
      final PsiExpression initializer = field.getInitializer();
      return initializer != null ? Pair.getSecond(StringExpressionHelper.evaluateExpression(initializer)) : null;
    }
  }
}
