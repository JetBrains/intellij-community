// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

@Presentation(icon = "AllIcons.Vcs.Branch")
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
