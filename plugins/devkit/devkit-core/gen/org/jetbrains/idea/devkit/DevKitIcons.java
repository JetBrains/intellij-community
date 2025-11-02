// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit;

import com.intellij.devkit.core.icons.DevkitCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @deprecated Use {@link DevkitCoreIcons} instead.
 */
@Deprecated(forRemoval = true)
public final class DevKitIcons {
  private DevKitIcons() {
  }

  /** @deprecated Use {@link DevkitCoreIcons#Add_sdk} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon Add_sdk = DevkitCoreIcons.Add_sdk;

  /** @deprecated Use {@link DevkitCoreIcons#ComposeToolWindow} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon ComposeToolWindow = DevkitCoreIcons.ComposeToolWindow;

  /**
   * @deprecated Use {@link DevkitCoreIcons.Gutter} instead.
   */
  @Deprecated(forRemoval = true)
  public static final class Gutter {
    private Gutter() {
    }

    /** @deprecated Use {@link DevkitCoreIcons.Gutter#DescriptionFile} */
    @Deprecated(forRemoval = true)
    public static final @NotNull Icon DescriptionFile = DevkitCoreIcons.Gutter.DescriptionFile;

    /** @deprecated Use {@link DevkitCoreIcons.Gutter#Diff} */
    @Deprecated(forRemoval = true)
    public static final @NotNull Icon Diff = DevkitCoreIcons.Gutter.Diff;

    /** @deprecated Use {@link DevkitCoreIcons.Gutter#Plugin} */
    @Deprecated(forRemoval = true)
    public static final @NotNull Icon Plugin = DevkitCoreIcons.Gutter.Plugin;

    /** @deprecated Use {@link DevkitCoreIcons.Gutter#Properties} */
    @Deprecated(forRemoval = true)
    public static final @NotNull Icon Properties = DevkitCoreIcons.Gutter.Properties;
  }

  /** @deprecated Use {@link DevkitCoreIcons#Items} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon Items = DevkitCoreIcons.Items;

  /** @deprecated Use {@link DevkitCoreIcons#LegacyPluginModule} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon LegacyPluginModule = DevkitCoreIcons.LegacyPluginModule;

  /** @deprecated Use {@link DevkitCoreIcons#PluginModule} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon PluginModule = DevkitCoreIcons.PluginModule;

  /** @deprecated Use {@link DevkitCoreIcons#PluginV2} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon PluginV2 = DevkitCoreIcons.PluginV2;

  /** @deprecated Use {@link DevkitCoreIcons#ProjectService} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon ProjectService = DevkitCoreIcons.ProjectService;

  /** @deprecated Use {@link DevkitCoreIcons#ProjectState} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon ProjectState = DevkitCoreIcons.ProjectState;

  /** @deprecated Use {@link DevkitCoreIcons#RemoteMapping} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon RemoteMapping = DevkitCoreIcons.RemoteMapping;

  /** @deprecated Use {@link DevkitCoreIcons#Sdk_closed} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon Sdk_closed = DevkitCoreIcons.Sdk_closed;

  /** @deprecated Use {@link DevkitCoreIcons#Service} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon Service = DevkitCoreIcons.Service;

  /** @deprecated Use {@link DevkitCoreIcons#State} */
  @Deprecated(forRemoval = true)
  public static final @NotNull Icon State = DevkitCoreIcons.State;
}
