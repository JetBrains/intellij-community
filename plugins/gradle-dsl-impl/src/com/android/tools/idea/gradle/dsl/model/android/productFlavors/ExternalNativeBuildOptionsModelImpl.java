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
package com.android.tools.idea.gradle.dsl.model.android.productFlavors;


import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.CMakeOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.NdkBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.CMakeOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.NdkBuildOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement.CMAKE_OPTIONS;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement.NDK_BUILD_OPTIONS;

public class ExternalNativeBuildOptionsModelImpl extends GradleDslBlockModel implements ExternalNativeBuildOptionsModel {
  public ExternalNativeBuildOptionsModelImpl(@NotNull ExternalNativeBuildOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public CMakeOptionsModel cmake() {
    CMakeOptionsDslElement cMakeOptionsDslElement = myDslElement.ensurePropertyElement(CMAKE_OPTIONS);
    return new CMakeOptionsModelImpl(cMakeOptionsDslElement);
  }

  @Override
  public void removeCMake() {
    myDslElement.removeProperty(CMAKE_OPTIONS.name);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel ndkBuild() {
    NdkBuildOptionsDslElement ndkBuildOptionsDslElement = myDslElement.ensurePropertyElement(NDK_BUILD_OPTIONS);
    return new NdkBuildOptionsModelImpl(ndkBuildOptionsDslElement);
  }

  @Override
  public void removeNdkBuild() {
    myDslElement.removeProperty(NDK_BUILD_OPTIONS.name);
  }
}
