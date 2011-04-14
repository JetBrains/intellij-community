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
package org.jetbrains.android.compiler;

import com.intellij.compiler.CompilerSettingsFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDexCompilerSettingsFactory implements CompilerSettingsFactory {
  @Override
  public Configurable create(Project project) {
    return new AndroidDexCompilerSettingsConfigurable(project);
  }

  private static class AndroidDexCompilerSettingsConfigurable implements SearchableConfigurable {
    private final AndroidDexCompilerConfiguration myConfig;
    private JPanel myContentPanel;
    private JSpinner myHeapSizeSpinner;
    private JLabel myVmOptionsLabel;
    private RawCommandLineEditor myVmOptionsEditor;

    public AndroidDexCompilerSettingsConfigurable(Project project) {
      myConfig = AndroidDexCompilerConfiguration.getInstance(project);
      myVmOptionsLabel.setLabelFor(myVmOptionsEditor);
      myVmOptionsEditor.setDialogCaption(AndroidBundle.message("android.dex.compiler.vm.options.title"));
    }

    @Nls
    @Override
    public String getDisplayName() {
      return AndroidBundle.message("android.dex.compiler.configurable.display.name");
    }

    @Override
    public Icon getIcon() {
      return AndroidUtils.ANDROID_ICON;
    }

    @Override
    public String getHelpTopic() {
      return "settings.android.dx.compiler";
    }

    @Override
    public JComponent createComponent() {
      return myContentPanel;
    }

    @Override
    public boolean isModified() {
      int maxHeapSize = ((Integer)myHeapSizeSpinner.getValue()).intValue();
      if (maxHeapSize != myConfig.MAX_HEAP_SIZE) {
        return true;
      }
      return !myVmOptionsEditor.getText().equals(myConfig.VM_OPTIONS);
    }

    @Override
    public void apply() throws ConfigurationException {
      myConfig.MAX_HEAP_SIZE = ((Integer)myHeapSizeSpinner.getValue()).intValue();
      myConfig.VM_OPTIONS = myVmOptionsEditor.getText();
    }

    @Override
    public void reset() {
      myHeapSizeSpinner.setModel(new SpinnerNumberModel(myConfig.MAX_HEAP_SIZE, 1, 10000000, 1));
      myVmOptionsEditor.setText(myConfig.VM_OPTIONS);
    }

    @Override
    public void disposeUIResources() {
    }

    @NotNull
    @Override
    public String getId() {
      return "android.dex.compiler";
    }

    @Override
    public Runnable enableSearch(String option) {
      return null;
    }
  }
}
