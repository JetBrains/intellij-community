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

package org.jetbrains.android.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PairFunction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class ExportSignedPackageAction extends AnAction {
  private static final String ANDROID_HIDE_SIGNED_EXPORT = "ANDROID_HIDE_SIGNED_EXPORT";

  public ExportSignedPackageAction() {
    super(AndroidBundle.message("android.export.signed.package.action.text"));
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && AndroidUtils.getApplicationFacets(project).size() > 0);

    final String hide = PropertiesComponent.getInstance().getValue(ANDROID_HIDE_SIGNED_EXPORT);
    if (Boolean.parseBoolean(hide)) {
      e.getPresentation().setVisible(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final String doNotShowStr = PropertiesComponent.getInstance().getValue(ANDROID_HIDE_SIGNED_EXPORT);
    final boolean doNotShow = Boolean.parseBoolean(doNotShowStr);

    if (!doNotShow) {
      final boolean[] hide = {false};
      final int result = Messages.showCheckboxMessageDialog("This action was moved to 'Build | Generate Signed APK...'.\n" +
                                                            "Also you can create Android artifact in 'File | Project Structure | Artifacts' to generate release APK",
                                                            "Export Signed Package",
                                                            new String[]{CommonBundle.getOkButtonText()},
                                                            "Hide 'Export Signed Application Package' in the menu", false, 0, 0,
                                                            Messages.getInformationIcon(),
                                                            new PairFunction<Integer, JCheckBox, Integer>() {
                                                              @Override
                                                              public Integer fun(Integer integer, JCheckBox checkBox) {
                                                                hide[0] = checkBox.isSelected();
                                                                return integer;
                                                              }
                                                            });

      if (result == Messages.OK && hide[0]) {
        PropertiesComponent.getInstance().setValue(ANDROID_HIDE_SIGNED_EXPORT, Boolean.toString(true));
      }
    }
  }
}
