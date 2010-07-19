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
package com.intellij.platform;

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;

/**
 * @author yole
 */
public class OpenDirectoryProjectAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    final FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(false);
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    FileChooser.chooseFilesWithSlideEffect(descriptor, project, null, new Consumer<VirtualFile[]>() {
      @Override
      public void consume(final VirtualFile[] files) {
        if (files.length > 0) {
          PlatformProjectOpenProcessor.getInstance().doOpenProject(files[0], null, false);
        }
      }
    });
  }
}
