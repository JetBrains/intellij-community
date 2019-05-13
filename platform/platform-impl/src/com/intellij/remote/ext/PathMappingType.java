/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remote.ext;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PathMappingType {
  public static final PathMappingType REPLICATED_FOLDER = new PathMappingType(AllIcons.Ide.Readonly, "Shared folders from Vagrantfile:");
  public static final PathMappingType DEPLOYMENT = new PathMappingType(AllIcons.Ide.Readonly, "From deployment configuration:");

  @Nullable private final Icon myIcon;
  @Nullable private final String myTooltip;

  public PathMappingType() {
    myIcon = null;
    myTooltip = null;
  }

  public PathMappingType(@Nullable Icon icon, @Nullable String tooltip) {
    myIcon = icon;
    myTooltip = tooltip;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public String getTooltip() {
    return myTooltip;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PathMappingType type = (PathMappingType)o;

    if (myTooltip != null ? !myTooltip.equals(type.myTooltip) : type.myTooltip != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myTooltip != null ? myTooltip.hashCode() : 0;
  }
}