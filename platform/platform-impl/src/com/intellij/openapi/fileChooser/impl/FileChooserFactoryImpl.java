// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.MacFileSaverDialog;
import com.intellij.ui.mac.MacPathChooserDialog;
import com.intellij.ui.win.WinPathChooserDialog;
import com.intellij.util.SystemProperties;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Set;

public class FileChooserFactoryImpl extends FileChooserFactory {
  @NotNull
  @Override
  public FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                             @Nullable Project project,
                                             @Nullable Component parent) {
    if (useNativeMacChooser(descriptor)) {
      return new MacPathChooserDialog(descriptor, parent, project);
    }
    if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, project);
    }
  }

  @Nullable
  public static PathChooserDialog createNativePathChooserIfEnabled(@NotNull FileChooserDescriptor descriptor, @Nullable Project project, @Nullable Component parent) {
    if (useNativeMacChooser(descriptor)) {
      return new MacPathChooserDialog(descriptor, parent, project);
    }
    else if (useNativeWinChooser()) {
      return new WinPathChooserDialog(descriptor, parent, project);
    }
    else {
      return null;
    }
  }

  @NotNull
  @Override
  public PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                             @Nullable Project project,
                                             @Nullable Component parent) {
    PathChooserDialog chooser = createNativePathChooserIfEnabled(descriptor, project, parent);
    if (chooser != null) {
      return chooser;
    }
    else if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, project);
    }
  }

  private static boolean useNativeWinChooser () {
    return SystemInfo.isWindows &&
           Registry.is("ide.win.file.chooser.native");
  }

  private static boolean useNativeMacChooser(final FileChooserDescriptor descriptor) {
    return SystemInfo.isMac &&
           !descriptor.isForcedToUseIdeaFileChooser() &&
           SystemProperties.getBooleanProperty("native.mac.file.chooser.enabled", true) &&
           Registry.is("ide.mac.file.chooser.native") &&
           SystemInfo.isJetBrainsJvm;
  }

  @NotNull
  @Override
  public FileTextField createFileTextField(@NotNull final FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent) {
    return new FileTextFieldImpl.Vfs(new JTextField(), getMacroMap(), parent, new LocalFsFinder.FileChooserFilter(descriptor, showHidden));
  }

  @Override
  public void installFileCompletion(@NotNull JTextField field,
                                    @NotNull FileChooserDescriptor descriptor,
                                    boolean showHidden,
                                    @Nullable Disposable parent) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      new FileTextFieldImpl.Vfs(field, getMacroMap(), parent, new LocalFsFinder.FileChooserFilter(descriptor, showHidden));
    }
  }

  public static Map<String, String> getMacroMap() {
    final PathMacros macros = PathMacros.getInstance();
    final Set<String> allNames = macros.getAllMacroNames();
    final Map<String, String> map = new THashMap<>(allNames.size());
    for (String eachMacroName : allNames) {
      map.put("$" + eachMacroName + "$", macros.getValue(eachMacroName));
    }

    return map;
  }

  @NotNull
  @Override
  public FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    return SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog")
           ? new MacFileSaverDialog(descriptor, project) : new FileSaverDialogImpl(descriptor, project);
  }

  @NotNull
  @Override
  public FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    return SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog")
           ? new MacFileSaverDialog (descriptor, parent) : new FileSaverDialogImpl(descriptor, parent);
  }
}
