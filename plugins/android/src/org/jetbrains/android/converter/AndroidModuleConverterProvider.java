/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.converter;

import com.intellij.conversion.ConverterProvider;
import com.intellij.conversion.ProjectConverter;
import com.intellij.conversion.ConversionContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AndroidModuleConverterProvider extends ConverterProvider {
  public AndroidModuleConverterProvider() {
    super("android-sdk-library");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Android SDK Library will be updated";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new AndroidProjectConverter();
  }
}
