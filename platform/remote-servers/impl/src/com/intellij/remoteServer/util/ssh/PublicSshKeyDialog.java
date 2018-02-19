/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.util.ssh;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author michael.golubev
 */
public class PublicSshKeyDialog extends DialogWrapper {

  private final PublicSshKeyFilePanel myPanel;

  public PublicSshKeyDialog(@Nullable Project project) {
    super(project);
    setTitle("Upload Public SSH Key");
    myPanel = new PublicSshKeyFilePanel();
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getMainPanel();
  }

  public File getSshKey() {
    return new File(myPanel.getSshKey());
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    return getSshKey().isFile() ? null : new ValidationInfo("Public SSH key file does not exist");
  }
}
