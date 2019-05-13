// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class AntIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AntIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }

  /**
   * 16x16
   */
  public static final Icon AntBuildXml = load("/icons/AntBuildXml.svg");
  /**
   * 16x16
   */
  public static final Icon Build = load("/icons/build.svg");
  /**
   * 16x16
   */
  public static final Icon LogDebug = load("/icons/logDebug.svg");
  /**
   * 16x16
   */
  public static final Icon LogVerbose = load("/icons/logVerbose.svg");
  /**
   * 16x16
   */
  public static final Icon MetaTarget = load("/icons/metaTarget.svg");
  /**
   * 16x16
   */
  public static final Icon Task = load("/icons/task.svg");
  /**
   * 16x16
   */
  public static final Icon Verbose = load("/icons/verbose.svg");

  /** @deprecated to be removed in IDEA 2020 - use AntIcons.Build */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon AntInstallation = AntIcons.Build;

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.General.Information */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon Message = load("/general/information.svg", com.intellij.icons.AllIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Actions.Properties */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon Properties = load("/actions/properties.svg", com.intellij.icons.AllIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Nodes.Target */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon Target = load("/nodes/target.svg", com.intellij.icons.AllIcons.class);
}
