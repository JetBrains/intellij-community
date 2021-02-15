/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.java;

import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementEnum;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementEnum.EnumValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslGlobalValue;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.CUSTOM;
import static com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil.convertToGradleString;
import static com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil.parseFromGradleString;

public class LanguageLevelPropertyModelImpl extends ResolvedPropertyModelImpl implements LanguageLevelPropertyModel {
  public LanguageLevelPropertyModelImpl(@NotNull GradlePropertyModelImpl realModel) {
    super(realModel);
  }

  @TestOnly
  @Override
  @Nullable
  public LanguageLevel toLanguageLevel() {
    String stringToParse = LanguageLevelUtil.getStringToParse(this);
    return stringToParse == null ? null : parseFromGradleString(stringToParse);
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel level) {
    setValue(convertToGradleString(level, LanguageLevelUtil.getStringToParse(this)));
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    GradleDslElement element = resolveModel().getElement();
    if (element != null && element.getParent() instanceof GradleDslElementEnum) {
      return CUSTOM;
    }
    return super.getValueType();
  }

  @Nullable
  @Override
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    if (getValueType() == CUSTOM && typeReference == STRING_TYPE) {
      GradleDslGlobalValue value = (GradleDslGlobalValue) resolveModel().getElement();
      EnumValue enumValue = (EnumValue) value.getValue();
      return (T)enumValue.getValue();

    }
    return super.getValue(typeReference);
  }

  @Nullable
  @Override
  public String toString() {
    if (getValueType() == CUSTOM) {
      return getValue(STRING_TYPE);
    }
    return super.toString();
  }
}
