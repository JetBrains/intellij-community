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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a reference to another property or variable.
 */
public final class ReferenceTo {
  @NotNull private static final String SIGNING_CONFIGS = "signingConfigs";
  @NotNull private String myReferenceText;

  public ReferenceTo(@NotNull String text) {
    myReferenceText = text;
  }

  public ReferenceTo(@NotNull GradlePropertyModel model) {
    myReferenceText = model.getFullyQualifiedName();
  }

  public ReferenceTo(@NotNull SigningConfigModel model) {
    myReferenceText = SIGNING_CONFIGS + "." + model.name();
  }

  public static ReferenceTo createForSigningConfig(@NotNull String signingConfigName) {
    return new ReferenceTo(SIGNING_CONFIGS + "." + signingConfigName);
  }

  @NotNull
  public String getText() {
    return myReferenceText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReferenceTo text = (ReferenceTo)o;
    return Objects.equal(myReferenceText, text.myReferenceText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myReferenceText);
  }

  @Override
  @NotNull
  public String toString() {
    return myReferenceText;
  }
}
