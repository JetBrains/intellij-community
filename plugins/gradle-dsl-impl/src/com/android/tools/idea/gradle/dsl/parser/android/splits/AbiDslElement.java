/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android.splits;

import static com.android.tools.idea.gradle.dsl.model.android.splits.AbiModelImpl.UNIVERSAL_APK;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class AbiDslElement extends BaseSplitOptionsDslElement {
  public static final PropertiesElementDescription<AbiDslElement> ABI =
    new PropertiesElementDescription<>("abi", AbiDslElement.class, AbiDslElement::new);

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap =
    Stream.concat(
      BaseSplitOptionsDslElement.ktsToModelNameMap.entrySet().stream().map(data -> new Object[]{
        data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().property, data.getValue().semantics
      }),
      Stream.of(new Object[][]{
        {"isUniversalApk", property, UNIVERSAL_APK, VAR},
      })).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap =
    Stream.concat(
      BaseSplitOptionsDslElement.groovyToModelNameMap.entrySet().stream().map(data -> new Object[]{
        data.getKey().getFirst(), data.getKey().getSecond(), data.getValue().property, data.getValue().semantics
      }),
      Stream.of(new Object[][]{
        {"universalApk", property, UNIVERSAL_APK, VAR},
        {"universalApk", exactly(1), UNIVERSAL_APK, SET},
      })).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter.isKotlin()) {
      return ktsToModelNameMap;
    }
    else if (converter.isGroovy()) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public AbiDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

}
