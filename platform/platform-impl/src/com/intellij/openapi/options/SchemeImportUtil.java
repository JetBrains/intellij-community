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
package com.intellij.openapi.options;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SchemeImportUtil {
  @Nullable
  public static VirtualFile selectImportSource(@NotNull final String[] sourceExtensions,
                                               @NotNull Component parent,
                                               @Nullable VirtualFile preselect) {
    final Set<String> extensions = new HashSet<>(Arrays.asList(sourceExtensions));
    FileChooserDialog fileChooser = FileChooserFactory.getInstance()
      .createFileChooser(new FileChooserDescriptor(true, false, false, false, false, false) {
        @Override
        public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
          return file.isDirectory() || extensions.contains(file.getExtension());
        }

        @Override
        public boolean isFileSelectable(VirtualFile file) {
          return !file.isDirectory() && extensions.contains(file.getExtension());
        }
      }, null, parent);
    final VirtualFile[] preselectFiles;
    if (preselect != null) {
      preselectFiles = new VirtualFile[1];
      preselectFiles[0] = preselect;
    }
    else {
      preselectFiles = VirtualFile.EMPTY_ARRAY;
    }
    final VirtualFile[] virtualFiles = fileChooser.choose(null, preselectFiles); 
                                                          //CodeStyleSchemesUIConfiguration.Util.getRecentImportFile());
    if (virtualFiles.length != 1) return null;
    virtualFiles[0].refresh(false, false);
    return virtualFiles[0];
  }

  public static void showStatus(final JComponent component, final String message, MessageType messageType) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message, messageType.getDefaultIcon(),
                                    messageType.getPopupBackground(), null);
    balloonBuilder.setFadeoutTime(5000);
    final Balloon balloon = balloonBuilder.createBalloon();
    balloon.showInCenterOf(component);
    Disposer.register(ProjectManager.getInstance().getDefaultProject(), balloon);
  }
}
