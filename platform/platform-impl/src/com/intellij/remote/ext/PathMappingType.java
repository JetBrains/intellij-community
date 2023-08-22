// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * @apiNote platform expects that every instance of this class is unique
 */
public final class PathMappingType {
  public static final PathMappingType REPLICATED_FOLDER = new PathMappingType(AllIcons.Ide.Readonly,
                                                                              IdeBundle
                                                                                .messagePointer("tooltip.shared.folders.from.vagrantfile"));
  public static final PathMappingType DEPLOYMENT = new PathMappingType(AllIcons.Ide.Readonly,
                                                                       IdeBundle.messagePointer("tooltip.from.deployment.configuration"));

  private final @Nullable Icon myIcon;
  private final @Nullable Supplier<@NlsContexts.Tooltip @Nullable String> myTooltipPointer;

  public PathMappingType(@Nullable Icon icon, @Nullable Supplier<@NlsContexts.Tooltip @Nullable String> tooltipPointer) {
    myIcon = icon;
    myTooltipPointer = tooltipPointer;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @NlsContexts.Tooltip @Nullable String getTooltip() {
    return myTooltipPointer == null ? null : myTooltipPointer.get();
  }
}