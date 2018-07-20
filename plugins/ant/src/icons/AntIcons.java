// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public class AntIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AntIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }

  public static final Icon AntBuildXml = load("/icons/AntBuildXml.png"); // 16x16
  public static final Icon AntInstallation = load("/icons/antInstallation.png"); // 16x16
  public static final Icon Build = load("/icons/build.png"); // 16x16
  public static final Icon LogDebug = load("/icons/logDebug.svg"); // 16x16
  public static final Icon LogVerbose = load("/icons/logVerbose.svg"); // 16x16
  public static final Icon Message = load("/icons/message.png"); // 16x16
  public static final Icon MetaTarget = load("/icons/metaTarget.png"); // 16x16

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Actions.Properties */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon Properties = load("/actions/properties.svg", com.intellij.icons.AllIcons.class);
  public static final Icon Target = load("/icons/target.png"); // 16x16
  public static final Icon Task = load("/icons/task.png"); // 16x16
  public static final Icon Verbose = load("/icons/verbose.png"); // 16x16
}
