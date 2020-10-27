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

import com.android.tools.idea.gradle.dsl.api.android.SplitsModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.AbiModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.DensityModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.LanguageModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.splits.AbiModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.splits.DensityModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.splits.LanguageModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement.ABI;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement.DENSITY;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement.LANGUAGE;

public class SplitsModelImpl extends GradleDslBlockModel implements SplitsModel {

  // TODO(xof): support abiFilters, densityFilters, languageFilters read-only properties?

  public SplitsModelImpl(@NotNull SplitsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public AbiModel abi() {
    AbiDslElement abiDslElement = myDslElement.ensurePropertyElement(ABI);
    return new AbiModelImpl(abiDslElement);
  }

  @Override
  public void removeAbi() {
    myDslElement.removeProperty(ABI.name);
  }


  @Override
  @NotNull
  public DensityModel density() {
    DensityDslElement densityDslElement = myDslElement.ensurePropertyElement(DENSITY);
    return new DensityModelImpl(densityDslElement);
  }

  @Override
  public void removeDensity() {
    myDslElement.removeProperty(DENSITY.name);
  }

  @Override
  @NotNull
  public LanguageModel language() {
    LanguageDslElement languageDslElement = myDslElement.ensurePropertyElement(LANGUAGE);
    return new LanguageModelImpl(languageDslElement);
  }

  @Override
  public void removeLanguage() {
    myDslElement.removeProperty(LANGUAGE.name);
  }
}
