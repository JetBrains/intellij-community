/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.dependencies.GradleDependenciesComparatorKt.CONFIGURATION_ORDERING;

public class DependenciesDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<DependenciesDslElement> DEPENDENCIES =
    new PropertiesElementDescription<>("dependencies", DependenciesDslElement.class, DependenciesDslElement::new);

  // TODO(xof): I wonder if there's a way of introspecting this list for any given Gradle and AGP version?
  //  (I made this list by looking in ~/.gradle/caches/6.1-milestone-2/kotlin-dsl-accessors/<>/cache/org/gradle/kotlin/dsl and running
  //    for file in *ConfigurationAccessors.kt; do
  //      printf \"%s%s\", $(echo ${file:0:1} | tr A-Z a-z) $(echo ${file:1} | sed s'/ConfigurationAccessors.kt//');
  //      echo;
  //    done
  //  which is not my finest hour)
  public static final Set<String> KTS_KNOWN_CONFIGURATIONS = new HashSet<>(
    Arrays.asList(
      "androidApis",
      "androidTestAnnotationProcessor",
      "androidTestApi",
      "androidTestApk",
      "androidTestCompile",
      "androidTestCompileOnly",
      "androidTestDebugAnnotationProcessor",
      "androidTestDebugApi",
      "androidTestDebugApk",
      "androidTestDebugCompile",
      "androidTestDebugCompileOnly",
      "androidTestDebugImplementation",
      "androidTestDebugProvided",
      "androidTestDebugRuntimeOnly",
      "androidTestDebugWearApp",
      "androidTestImplementation",
      "androidTestProvided",
      "androidTestRuntimeOnly",
      "androidTestUtil",
      "androidTestWearApp",
      "annotationProcessor",
      "api",
      "apk",
      "archives",
      "compile",
      "compileOnly",
      "coreLibraryDesugaring",
      "debugAnnotationProcessor",
      "debugApi",
      "debugApk",
      "debugCompile",
      "debugCompileOnly",
      "debugImplementation",
      "debugProvided",
      "debugRuntimeOnly",
      "debugWearApp",
      "default",
      "implementation",
      "lintChecks",
      "lintClassPath",
      "lintPublish",
      "provided",
      "releaseAnnotationProcessor",
      "releaseApi",
      "releaseApk",
      "releaseCompile",
      "releaseCompileOnly",
      "releaseImplementation",
      "releaseProvided",
      "releaseRuntimeOnly",
      "releaseWearApp",
      "runtimeOnly",
      "testAnnotationProcessor",
      "testApi",
      "testApk",
      "testCompile",
      "testCompileOnly",
      "testDebugAnnotationProcessor",
      "testDebugApi",
      "testDebugApk",
      "testDebugCompile",
      "testDebugCompileOnly",
      "testDebugImplementation",
      "testDebugProvided",
      "testDebugRuntimeOnly",
      "testDebugWearApp",
      "testImplementation",
      "testProvided",
      "testReleaseAnnotationProcessor",
      "testReleaseApi",
      "testReleaseApk",
      "testReleaseCompile",
      "testReleaseCompileOnly",
      "testReleaseImplementation",
      "testReleaseProvided",
      "testReleaseRuntimeOnly",
      "testReleaseWearApp",
      "testRuntimeOnly",
      "testWearApp",
      "wearApp"
    )
  );



  public static final Comparator comparator = Comparator.comparing(GradleDslElement::getName, CONFIGURATION_ORDERING);

  public DependenciesDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement dependency) {
    // Treat all expressions and expression maps as dependencies
    if (dependency instanceof GradleDslSimpleExpression ||
        dependency instanceof GradleDslExpressionMap ||
        dependency instanceof GradleDslExpressionList) {
      super.addParsedElement(dependency);
    }
  }

  @Override
  @NotNull
  public GradleDslElement setNewElement(@NotNull GradleDslElement newElement) {
    List<GradleDslElement> es = getAllElements();
    int i = 0;
    for (; i < es.size(); i++) {
      if (comparator.compare(es.get(i), newElement) > 0) {
        break;
      }
    }
    addNewElementAt(i, newElement);
    return newElement;
  }
}
