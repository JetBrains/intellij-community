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
package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.SigningConfigPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface FlavorTypeModel extends GradleDslModel {
  @NotNull
  String name();

  /**
   * Renames this FlavorTypeModel, this only changes the name where the model is first defined it does not
   * attempt to update any references to the model.
   *
   * @param newName the new name
   */
  void rename(@NotNull String newName);

  @NotNull
  ResolvedPropertyModel applicationIdSuffix();

  @NotNull
  List<BuildConfigField> buildConfigFields();

  BuildConfigField addBuildConfigField(@NotNull String type, @NotNull String name, @NotNull String value);

  void removeBuildConfigField(@NotNull String type, @NotNull String name, @NotNull String value);

  BuildConfigField replaceBuildConfigField(@NotNull String oldType,
                                                          @NotNull String oldName,
                                                          @NotNull String oldValue,
                                                          @NotNull String type,
                                                          @NotNull String name,
                                                          @NotNull String value);

  void removeAllBuildConfigFields();

  @NotNull
  ResolvedPropertyModel consumerProguardFiles();

  @NotNull
  ResolvedPropertyModel manifestPlaceholders();

  @NotNull
  ResolvedPropertyModel matchingFallbacks();

  @NotNull
  ResolvedPropertyModel multiDexEnabled();

  @NotNull
  ResolvedPropertyModel multiDexKeepFile();

  @NotNull
  ResolvedPropertyModel multiDexKeepProguard();

  @NotNull
  ResolvedPropertyModel proguardFiles();

  @NotNull
  List<ResValue> resValues();

  ResValue addResValue(@NotNull String type, @NotNull String name, @NotNull String value);

  void removeResValue(@NotNull String type, @NotNull String name, @NotNull String value);

  ResValue replaceResValue(@NotNull String oldType,
                           @NotNull String oldName,
                           @NotNull String oldValue,
                           @NotNull String type,
                           @NotNull String name,
                           @NotNull String value);

  void removeAllResValues();

  /**
   * You most likely want to set this property as a reference to a signing config,
   * to do this please use {@link ReferenceTo#ReferenceTo(SigningConfigModel)}.
   *
   * You can obtain a list of signing configs from {@link AndroidModel#signingConfigs()}
   */
  @NotNull
  SigningConfigPropertyModel signingConfig();

  @NotNull
  ResolvedPropertyModel useJack();


  @NotNull
  ResolvedPropertyModel versionNameSuffix();

  /**
   * Represents a statement like {@code resValue} or {@code buildConfigField} which contains type, name and value parameters.
   */
  interface TypeNameValueElement {

    @NotNull
    ResolvedPropertyModel name();

    @NotNull
    ResolvedPropertyModel value();

    @NotNull
    ResolvedPropertyModel type();

    @NotNull
    String elementName();

    void remove();

    GradlePropertyModel getModel();
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  interface ResValue extends TypeNameValueElement {
  }


  /**
   * Represents a {@code buildConfigField} statement defined in the build type block of the Gradle file.
   */
  interface BuildConfigField extends TypeNameValueElement {
  }
}
