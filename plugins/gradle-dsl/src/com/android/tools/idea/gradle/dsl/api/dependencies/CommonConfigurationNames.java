/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.dependencies;

import org.jetbrains.annotations.NonNls;

/**
 * Common configuration names used in dependencies.
 */
public final class CommonConfigurationNames {
  @NonNls public static final String ANDROID_TEST_API = "androidTestApi";
  @NonNls public static final String ANDROID_TEST_COMPILE = "androidTestCompile";
  @NonNls public static final String ANDROID_TEST_IMPLEMENTATION = "androidTestImplementation";
  @NonNls public static final String API = "api";
  @NonNls public static final String APK = "apk";
  @NonNls public static final String CLASSPATH = "classpath";
  @NonNls public static final String COMPILE = "compile";
  @NonNls public static final String IMPLEMENTATION = "implementation";
  @NonNls public static final String PROVIDED = "provided";
  @NonNls public static final String RUNTIME = "runtime";
  @NonNls public static final String TEST_API = "testApi";
  @NonNls public static final String TEST_COMPILE = "testCompile";
  @NonNls public static final String TEST_IMPLEMENTATION = "testImplementation";

  private CommonConfigurationNames() {
  }
}
