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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.KEY_ALIAS;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.KEY_PASSWORD;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_FILE;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_PASSWORD;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_TYPE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SigningConfigDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<SigningConfigDslElement> SIGNING_CONFIG =
    new PropertiesElementDescription<>(null, SigningConfigDslElement.class, SigningConfigDslElement::new);

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"keyAlias", property, KEY_ALIAS, VAR},
    {"setKeyAlias", exactly(1), KEY_ALIAS, SET},
    {"keyPassword", property, KEY_PASSWORD, VAR},
    {"setKeyPassword", exactly(1), KEY_PASSWORD, SET},
    {"storeFile", property, STORE_FILE, VAR},
    {"setStoreFile", exactly(1), STORE_FILE, SET},
    {"storePassword", property, STORE_PASSWORD, VAR},
    {"setStorePassword", exactly(1), STORE_PASSWORD, SET},
    {"storeType", property, STORE_TYPE, VAR},
    {"setStoreType", exactly(1), STORE_TYPE, SET},
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"keyAlias", property, KEY_ALIAS, VAR},
    {"keyAlias", exactly(1), KEY_ALIAS, SET},
    {"keyPassword", property, KEY_PASSWORD, VAR},
    {"keyPassword", exactly(1), KEY_PASSWORD, SET},
    {"storeFile", property, STORE_FILE, VAR},
    {"storeFile", exactly(1), STORE_FILE, SET},
    {"storePassword", property, STORE_PASSWORD, VAR},
    {"storePassword", exactly(1), STORE_PASSWORD, SET},
    {"storeType", property, STORE_TYPE, VAR},
    {"storeType", exactly(1), STORE_TYPE, SET},
  }).collect(toModelMap());

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

  @Nullable
  private String methodName;

  @Override
  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  @Override
  public String getMethodName() {
    return  methodName;
  }

  public SigningConfigDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // the debug signingConfig is automatically created
    return getName().equals("debug");
  }
}
