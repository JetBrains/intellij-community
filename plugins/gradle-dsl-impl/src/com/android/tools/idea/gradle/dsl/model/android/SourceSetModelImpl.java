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
package com.android.tools.idea.gradle.dsl.model.android;

import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.AIDL;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.ASSETS;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.JAVA;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.JNI;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.JNI_LIBS;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.ML_MODELS;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.RENDERSCRIPT;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.RES;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.RESOURCES;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement.SHADERS;
import static com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement.MANIFEST;

import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceDirectoryModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.sourceSets.SourceFileModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SourceSetModelImpl extends GradleDslBlockModel implements SourceSetModel {
  @NonNls public static final String ROOT = "mRoot";

  public SourceSetModelImpl(@NotNull SourceSetDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  public void rename(@NotNull String newName) {
    myDslElement.getNameElement().rename(newName);
    myDslElement.setModified();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel root() {
    return getModelForProperty(ROOT);
  }

  @Override
  @NotNull
  public SourceDirectoryModel aidl() {
    SourceDirectoryDslElement aidl = myDslElement.ensurePropertyElement(AIDL);
    return new SourceDirectoryModelImpl(aidl);
  }

  @Override
  public void removeAidl() {
    myDslElement.removeProperty(AIDL.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel assets() {
    SourceDirectoryDslElement assets = myDslElement.ensurePropertyElement(ASSETS);
    return new SourceDirectoryModelImpl(assets);
  }

  @Override
  public void removeAssets() {
    myDslElement.removeProperty(ASSETS.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel java() {
    SourceDirectoryDslElement java = myDslElement.ensurePropertyElement(JAVA);
    return new SourceDirectoryModelImpl(java);
  }

  @Override
  public void removeJava() {
    myDslElement.removeProperty(JAVA.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel jni() {
    SourceDirectoryDslElement jni = myDslElement.ensurePropertyElement(JNI);
    return new SourceDirectoryModelImpl(jni);
  }

  @Override
  public void removeJni() {
    myDslElement.removeProperty(JNI.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel jniLibs() {
    SourceDirectoryDslElement jniLibs = myDslElement.ensurePropertyElement(JNI_LIBS);
    return new SourceDirectoryModelImpl(jniLibs);
  }

  @Override
  public void removeJniLibs() {
    myDslElement.removeProperty(JNI_LIBS.name);
  }

  @Override
  @NotNull
  public SourceFileModel manifest() {
    SourceFileDslElement manifest = myDslElement.ensurePropertyElement(MANIFEST);
    return new SourceFileModelImpl(manifest);
  }

  @Override
  public void removeManifest() {
    myDslElement.removeProperty(MANIFEST.name);
  }

  @NotNull
  @Override
  public SourceDirectoryModel mlModels() {
    SourceDirectoryDslElement mlModels = myDslElement.ensurePropertyElement(ML_MODELS);
    return new SourceDirectoryModelImpl(mlModels);
  }

  @Override
  public void removeMlModels() {
    myDslElement.removeProperty(ML_MODELS.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel renderscript() {
    SourceDirectoryDslElement renderscript = myDslElement.ensurePropertyElement(RENDERSCRIPT);
    return new SourceDirectoryModelImpl(renderscript);
  }

  @Override
  public void removeRenderscript() {
    myDslElement.removeProperty(RENDERSCRIPT.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel res() {
    SourceDirectoryDslElement res = myDslElement.ensurePropertyElement(RES);
    return new SourceDirectoryModelImpl(res);
  }

  @Override
  public void removeRes() {
    myDslElement.removeProperty(RES.name);
  }

  @Override
  @NotNull
  public SourceDirectoryModel resources() {
    SourceDirectoryDslElement resources = myDslElement.ensurePropertyElement(RESOURCES);
    return new SourceDirectoryModelImpl(resources);
  }

  @Override
  public void removeResources() {
    myDslElement.removeProperty(RESOURCES.name);
  }

  @NotNull
  @Override
  public SourceDirectoryModel shaders() {
    SourceDirectoryDslElement shaders = myDslElement.ensurePropertyElement(SHADERS);
    return new SourceDirectoryModelImpl(shaders);
  }

  @Override
  public void removeShaders() {
    myDslElement.removeProperty(SHADERS.name);
  }
}
