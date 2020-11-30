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

import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.ADDITIONAL_PARAMETERS;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.JAVA_MAX_HEAP_SIZE;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.JUMBO_MODE;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.KEEP_RUNTIME_ANNOTATED_CLASSES;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.MAX_PROCESS_COUNT;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.OPTIMIZE;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.PRE_DEX_LIBRARIES;
import static com.android.tools.idea.gradle.dsl.model.android.DexOptionsModelImpl.THREAD_COUNT;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class DexOptionsDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"additionalParameters", property, ADDITIONAL_PARAMETERS, VAR},
    {"additionalParameters", atLeast(0), ADDITIONAL_PARAMETERS, ADD_AS_LIST},
    {"javaMaxHeapSize", property, JAVA_MAX_HEAP_SIZE, VAR},
    {"setJavaMaxHeapSize", exactly(1), JAVA_MAX_HEAP_SIZE, SET},
    {"jumboMode", property, JUMBO_MODE, VAR},
    {"setJumboMode", exactly(1), JUMBO_MODE, SET},
    {"keepRuntimeAnnotatedClasses", property, KEEP_RUNTIME_ANNOTATED_CLASSES, VAR},
    {"setKeepRuntimeAnnotatedClasses", exactly(1), KEEP_RUNTIME_ANNOTATED_CLASSES, SET},
    {"maxProcessCount", property, MAX_PROCESS_COUNT, VAR},
    {"setMaxProcessCount", exactly(1), MAX_PROCESS_COUNT, SET},
    {"optimize", property, OPTIMIZE, VAR},
    {"setOptimize", exactly(1), OPTIMIZE, SET},
    {"preDexLibraries", property, PRE_DEX_LIBRARIES, VAR},
    {"setPreDexLibraries", exactly(1), PRE_DEX_LIBRARIES, SET},
    {"threadCount", property, THREAD_COUNT, VAR},
    {"setThreadCount", exactly(1), THREAD_COUNT, SET}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"additionalParameters", property, ADDITIONAL_PARAMETERS, VAR},
    {"additionalParameters", atLeast(0), ADDITIONAL_PARAMETERS, ADD_AS_LIST},
    {"javaMaxHeapSize", property, JAVA_MAX_HEAP_SIZE, VAR},
    {"javaMaxHeapSize", exactly(1), JAVA_MAX_HEAP_SIZE, SET},
    {"jumboMode", property, JUMBO_MODE, VAR},
    {"jumboMode", exactly(1), JUMBO_MODE, SET},
    {"keepRuntimeAnnotatedClasses", property, KEEP_RUNTIME_ANNOTATED_CLASSES, VAR},
    {"keepRuntimeAnnotatedClasses", exactly(1), KEEP_RUNTIME_ANNOTATED_CLASSES, SET},
    {"maxProcessCount", property, MAX_PROCESS_COUNT, VAR},
    {"maxProcessCount", exactly(1), MAX_PROCESS_COUNT, SET},
    {"optimize", property, OPTIMIZE, VAR},
    {"optimize", exactly(1), OPTIMIZE, SET},
    {"preDexLibraries", property, PRE_DEX_LIBRARIES, VAR},
    {"preDexLibraries", exactly(1), PRE_DEX_LIBRARIES, SET},
    {"threadCount", property, THREAD_COUNT, VAR},
    {"threadCount", exactly(1), THREAD_COUNT, SET}
  }).collect(toModelMap());
  public static final PropertiesElementDescription<DexOptionsDslElement> DEX_OPTIONS =
    new PropertiesElementDescription<>("dexOptions", DexOptionsDslElement.class, DexOptionsDslElement::new);

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

  public DexOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
