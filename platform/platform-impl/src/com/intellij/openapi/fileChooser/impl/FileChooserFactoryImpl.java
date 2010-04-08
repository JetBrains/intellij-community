/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.MacFileChooserDialog;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FileChooserFactoryImpl extends FileChooserFactory {
  public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Project project) {
    if (SystemInfo.isMac && System.getProperty("idea.use.native.mac.filechooser", Boolean.FALSE.toString()).equals(Boolean.TRUE.toString())) {
      return new MacFileChooserDialog(descriptor);
    }

    return new FileChooserDialogImpl(descriptor, project);
  }

  public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Component parent) {
    if (SystemInfo.isMac && System.getProperty("idea.use.native.mac.filechooser", Boolean.FALSE.toString()).equals(Boolean.TRUE.toString())) {
      return new MacFileChooserDialog(descriptor);
    }

    return new FileChooserDialogImpl(descriptor, parent);
  }

  public FileTextField createFileTextField(final FileChooserDescriptor descriptor, final boolean showHidden, Disposable parent) {
    return new FileTextFieldImpl.Vfs(descriptor, showHidden, new JTextField(), getMacroMap(), parent);
  }

  public FileTextField createFileTextField(final FileChooserDescriptor descriptor, Disposable parent) {
    return createFileTextField(descriptor, true, parent);
  }

  public void installFileCompletion(final JTextField field, final FileChooserDescriptor descriptor, final boolean showHidden,
                                    final Disposable parent) {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

    FileTextFieldImpl.Vfs vfsField = new FileTextFieldImpl.Vfs(descriptor, showHidden, field, getMacroMap(), parent);
  }

  public static Map<String, String> getMacroMap() {
    final PathMacros macros = PathMacros.getInstance();
    final Set<String> allNames = macros.getAllMacroNames();
    final HashMap<String, String> map = new HashMap<String, String>();
    for (String eachMacroName : allNames) {
      map.put("$" + eachMacroName + "$", macros.getValue(eachMacroName));
    }

    return map;
  }

  public FileSaverDialog createSaveFileDialog(FileSaverDescriptor descriptor, Project project) {
    return new FileSaverDialogImpl(descriptor, project);
  }
}
