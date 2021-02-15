/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.groovy;

import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.convertToExternalTextValue;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.decodeStringLiteral;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.getGradleNameForPsiElement;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.gradleNameFor;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.isStringLiteral;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO;

import com.android.tools.idea.gradle.dsl.api.util.GradleNameElementUtil;
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import java.util.Map;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public abstract class GroovyDslNameConverter implements GradleDslNameConverter {

  @Override
  public boolean isGroovy() {
    return true;
  }

  @Override
  public boolean isKotlin() {
    return false;
  }

  @NotNull
  @Override
  public String psiToName(@NotNull PsiElement element) {
    // TODO(xof): I think that this might be unnecessary once psiToName is implemented in terms of gradleNameFor()
    //  because the treatment of GrReferenceExpressions may handle escapes in string identifiers
    //  automatically.
    if (isStringLiteral(element)) {
      StringBuilder sb = new StringBuilder();
      if (decodeStringLiteral(element, sb)) {
        return GradleNameElementUtil.escape(sb.toString());
      }
    }
    // TODO(xof): the project-massaging in getGradleNameForPsiElement should be rewritten in gradleNameFor
    return getGradleNameForPsiElement(element);
  }

  @NotNull
  @Override
  public String convertReferenceText(@NotNull GradleDslElement context, @NotNull String referenceText) {
    String result = ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getDslFile().getProject());
      GrExpression expression = factory.createExpressionFromText(referenceText);
      return gradleNameFor(expression);
    });
    return result != null ? result : referenceText;
  }

  @NotNull
  @Override
  public String convertReferenceToExternalText(@NotNull GradleDslElement context, @NotNull String referenceText, boolean forInjection) {
    if (context instanceof GradleDslSimpleExpression) {
      return convertToExternalTextValue((GradleDslSimpleExpression)context, context.getDslFile(), referenceText, forInjection);
    }
    else {
      return referenceText;
    }
  }

  @NotNull
  @Override
  public ExternalNameInfo externalNameForParent(@NotNull String modelName, @NotNull GradleDslElement context) {
    ImmutableMap<Pair<String,Integer>, ModelEffectDescription> map = context.getExternalToModelMap(this);
    ExternalNameInfo result = new ExternalNameInfo(modelName, null);
    for (Map.Entry<Pair<String,Integer>, ModelEffectDescription> e : map.entrySet()) {
      if (e.getValue().property.name.equals(modelName)) {
        SemanticsDescription semantics = e.getValue().semantics;
        if (semantics == SET || semantics == ADD_AS_LIST || semantics == OTHER) {
          return new ExternalNameInfo(e.getKey().getFirst(), true);
        }
        if (semantics == VAR || semantics == VWO || semantics == VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS) {
          result = new ExternalNameInfo(e.getKey().getFirst(), false);
        }
      }
    }
    return result;
  }

  @Nullable
  @Override
  public ModelPropertyDescription modelDescriptionForParent(@NotNull String externalName, @NotNull GradleDslElement context) {
    ImmutableMap<Pair<String,Integer>, ModelEffectDescription> map = context.getExternalToModelMap(this);
    for (Map.Entry<Pair<String,Integer>, ModelEffectDescription> e : map.entrySet()) {
      if (e.getKey().getFirst().equals(externalName)) {
        return e.getValue().property;
      }
    }
    return null;
  }
}
