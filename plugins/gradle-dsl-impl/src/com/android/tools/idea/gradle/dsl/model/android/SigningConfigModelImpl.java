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

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SigningConfigModelImpl extends GradleDslBlockModel implements SigningConfigModel {
  @NonNls public static final String STORE_FILE = "mStoreFile";
  @NonNls public static final String STORE_PASSWORD = "mStorePassword";
  @NonNls public static final String STORE_TYPE = "mStoreType";
  @NonNls public static final String KEY_ALIAS = "mKeyAlias";
  @NonNls public static final String KEY_PASSWORD = "mKeyPassword";

  public SigningConfigModelImpl(@NotNull SigningConfigDslElement dslElement) {
    super(dslElement);
    myDslElement = dslElement;
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  public void rename(@NotNull String newName, boolean renameReferences) {
    myDslElement.getNameElement().rename(newName);
    myDslElement.setModified();

    if (renameReferences) {
      // TODO(b/145395390): this merely captures dependents in the Dsl model.  There might be KotlinScript code somewhere in the user's
      //  build that references the signingConfig by name, which we could in principle find by resolving the Psi: those should arguably
      //  be renamed too.
      for (GradleReferenceInjection dependent : myDslElement.getDependents()) {
        dependent.getOriginElement().setValue(new ReferenceTo(this));
      }
      renameModelDependents(storePassword());
      renameModelDependents(storeFile());
      renameModelDependents(storeType());
      renameModelDependents(keyAlias());
      renameModelDependents(keyPassword());
    }
  }

  private static void renameModelDependents(GradlePropertyModel model) {
    GradleDslElement element = (GradleDslElement)model.getRawElement();
    if (element != null) {
      for (GradleReferenceInjection dependent : element.getDependents()) {
        // NB the new ReferenceTo(...) here will bypass the SigningConfig method
        dependent.getOriginElement().setValue(new ReferenceTo(model));
      }
    }
  }

  @Override
  @NotNull
  public ResolvedPropertyModel storeFile() {
    return getFileModelForProperty(STORE_FILE);
  }

  @Override
  @NotNull
  public PasswordPropertyModel storePassword() {
    return getPasswordModelForProperty(STORE_PASSWORD);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel storeType() {
    return getModelForProperty(STORE_TYPE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel keyAlias() {
    return getModelForProperty(KEY_ALIAS);
  }

  @Override
  @NotNull
  public PasswordPropertyModel keyPassword() {
    return getPasswordModelForProperty(KEY_PASSWORD);
  }
}
