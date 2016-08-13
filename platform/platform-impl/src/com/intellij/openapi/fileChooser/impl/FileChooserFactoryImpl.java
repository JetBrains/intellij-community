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
import com.intellij.ui.mac.MacPathChooserDialog;
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

  @NotNull
  @Override
  public PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                             @Nullable Project project,
                                             @Nullable Component parent) {
    if (useNativeMacChooser(descriptor)) {
      return new MacPathChooserDialog(descriptor, parent, project);
    }
    else if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, project);
    }
  }

  private static boolean useNativeMacChooser(final FileChooserDescriptor descriptor) {
    return SystemInfo.isMac &&
           SystemProperties.getBooleanProperty("native.mac.file.chooser.enabled", true) &&
           Registry.is("ide.mac.file.chooser.native") /*&&
           !DialogWrapper.isMultipleModalDialogs()*/;
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
    return new FileSaverDialogImpl(descriptor, project);
  }

  @NotNull
  @Override
  public FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    return new FileSaverDialogImpl(descriptor, parent);
  }
}
