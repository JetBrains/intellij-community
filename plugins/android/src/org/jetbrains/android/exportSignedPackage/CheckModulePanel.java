/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.exportSignedPackage;

import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene.Kudelevsky
 */
public class CheckModulePanel extends JPanel {
  private boolean myHasError;
  private boolean myHasWarnings;

  public CheckModulePanel() {
    super(new VerticalFlowLayout(FlowLayout.LEFT));
  }

  public void updateMessages(AndroidFacet facet) {
    Module module = facet.getModule();
    clearMessages();
    final DummyCompileContext compileContext = DummyCompileContext.getInstance();
    VirtualFile outputDirectory = compileContext.getModuleOutputDirectory(module);
    if (outputDirectory != null) {
      /*String apkFilePath = facet.getApkPath();
      File f = new File(apkFilePath);
      if (!f.isFile()) {
        addError(AndroidBundle.message("android.file.not.exist.error", f.getPath()));
      }*/
    }
    else {
      addError(AndroidBundle.message("android.unable.to.get.output.directory.error"));
    }

    /*Manifest manifest = facet.getManifest();
    assert manifest != null;
    Application application = manifest.getApplication();
    assert application != null;
    String debuggable = application.getDebuggable().getValue();
    if (debuggable != null && BooleanValueConverter.getInstance(true).isTrue(debuggable)) {
      addWarning(AndroidBundle.message("android.export.signed.package.debuggable.warning"));
    }*/
  }

  public boolean hasError() {
    return myHasError;
  }

  public boolean hasWarnings() {
    return myHasWarnings;
  }

  public void clearMessages() {
    removeAll();
    myHasError = false;
    myHasWarnings = false;
  }

  public void addError(String message) {
    JLabel label = new JLabel();
    label.setIcon(Messages.getErrorIcon());
    label.setText("<html><body><b>Error: " + message + "</b></body></html>");
    add(label);
    myHasError = true;
  }

  public void addWarning(String message) {
    JLabel label = new JLabel();
    label.setIcon(Messages.getWarningIcon());
    label.setText("<html><body><b>Warning: " + message + "</b></body></html>");
    add(label);
    myHasWarnings = true;
  }
}
