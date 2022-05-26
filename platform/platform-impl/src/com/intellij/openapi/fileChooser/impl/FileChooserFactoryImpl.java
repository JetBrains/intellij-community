// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
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
    else if (useNativeWinChooser(descriptor)) {
      return new WinPathChooserDialog(descriptor, parent, project);
    }
    else if (useNewChooser()) {
      return new NewFileChooserDialogImpl(descriptor, parent, project);
    }
    else if (parent != null) {
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
    else if (useNativeWinChooser(descriptor)) {
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
    else if (useNewChooser()) {
      return new NewFileChooserDialogImpl(descriptor, parent, project);
    }
    else if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, project);
    }
  }

  private static boolean useNewChooser() {
    return Registry.is("ide.ui.new.chooser");
  }

  private static boolean useNativeWinChooser(FileChooserDescriptor descriptor) {
    return SystemInfo.isWindows &&
           !descriptor.isForcedToUseIdeaFileChooser() &&
           Registry.is("ide.win.file.chooser.native", false);
  }

  private static boolean useNativeMacChooser(FileChooserDescriptor descriptor) {
    return SystemInfo.isMac &&
           SystemInfo.isJetBrainsJvm &&
           !descriptor.isForcedToUseIdeaFileChooser() &&
           Registry.is("ide.mac.file.chooser.native", true);
  }

  @NotNull
  @Override
  public FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent) {
    return new FileTextFieldImpl(new JTextField(), new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(descriptor, showHidden), getMacroMap(), parent);
  }

  @Override
  public void installFileCompletion(@NotNull JTextField field,
                                    @NotNull FileChooserDescriptor descriptor,
                                    boolean showHidden,
                                    @Nullable Disposable parent) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      new FileTextFieldImpl(field, new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(descriptor, showHidden), getMacroMap(), parent);
    }
  }

  public static Map<String, String> getMacroMap() {
    PathMacros macros = PathMacros.getInstance();
    Set<String> allNames = macros.getAllMacroNames();
    Map<String, String> map = new HashMap<>(allNames.size());
    for (String eachMacroName : allNames) {
      map.put("$" + eachMacroName + "$", macros.getValue(eachMacroName));
    }
    return map;
  }

  @NotNull
  @Override
  public FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    return SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog", true)
           ? new MacFileSaverDialog(descriptor, project) : new FileSaverDialogImpl(descriptor, project);
  }

  @NotNull
  @Override
  public FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    return SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog", true)
           ? new MacFileSaverDialog (descriptor, parent) : new FileSaverDialogImpl(descriptor, parent);
  }
}
