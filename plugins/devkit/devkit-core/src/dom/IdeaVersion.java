/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom;

import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

public interface IdeaVersion extends DomElement {

  @NotNull
  @Required
  @Stubbed
  @Convert(BuildNumberConverter.class)
  GenericAttributeValue<BuildNumber> getSinceBuild();

  @NotNull
  @Stubbed
  @Convert(BuildNumberConverter.class)
  GenericAttributeValue<BuildNumber> getUntilBuild();


  /**
   * @deprecated Use {@link #getSinceBuild()}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  @Deprecated
  GenericAttributeValue<String> getMin();

  /**
   * @deprecated Use {@link #getUntilBuild()}
   */
  @NotNull
  @Deprecated
  GenericAttributeValue<String> getMax();


  class BuildNumberConverter extends Converter<BuildNumber> {

    @Nullable
    @Override
    public BuildNumber fromString(@Nullable String s, ConvertContext context) {
      return s == null ? null : BuildNumber.fromStringOrNull(s);
    }

    @Nullable
    @Override
    public String toString(@Nullable BuildNumber number, ConvertContext context) {
      return number == null ? null : number.asString();
    }

    @Nullable
    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return DevKitBundle.message("inspections.plugin.xml.invalid.build.number", s);
    }
  }
}
