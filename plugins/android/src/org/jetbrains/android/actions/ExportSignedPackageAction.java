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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PairFunction;
import org.jetbrains.android.exportSignedPackage.CheckModulePanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class ExportSignedPackageAction extends AnAction {
  static final String HIDE_EXPORT_ACTIONS_PROPERTY = "ANDROID_HIDE_EXPORT_ACTIONS";

  public ExportSignedPackageAction() {
    super(AndroidBundle.message("android.export.signed.package.action.text"));
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && AndroidUtils.getApplicationFacets(project).size() > 0);

    final String hide = PropertiesComponent.getInstance().getValue(HIDE_EXPORT_ACTIONS_PROPERTY);
    if (Boolean.parseBoolean(hide)) {
      e.getPresentation().setVisible(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showNotification(true);

    /*final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    List<AndroidFacet> facets = AndroidUtils.getApplicationFacets(project);
    assert facets.size() > 0;
    if (facets.size() == 1) {
      if (!checkFacet(facets.get(0))) return;
    }
    ExportSignedPackageWizard wizard = new ExportSignedPackageWizard(project, facets, true);
    wizard.show();*/
  }

  static void showNotification(boolean signed) {
    final String doNotShowStr = PropertiesComponent.getInstance().getValue(HIDE_EXPORT_ACTIONS_PROPERTY);
    final boolean doNotShow = Boolean.parseBoolean(doNotShowStr);

    if (!doNotShow) {
      final boolean[] hide = {false};
      final int result = Messages.showCheckboxMessageDialog(
        "There is no 'Export package' wizard since IDEA 12. Instead, please open\n" +
        "'File | Project Structure | Artifacts' and create new 'Android Application' artifact.",
        signed ? "Export Signed Package" : "Export Unsigned Package",
        new String[]{CommonBundle.getOkButtonText()}, "Hide 'Export package' actions in the menu", false, 0, 0,
        Messages.getInformationIcon(), new PairFunction<Integer, JCheckBox, Integer>() {
        @Override
        public Integer fun(Integer integer, JCheckBox checkBox) {
          hide[0] = checkBox.isSelected();
          return integer;
        }
      });

      if (result == Messages.OK && hide[0]) {
        PropertiesComponent.getInstance().setValue(HIDE_EXPORT_ACTIONS_PROPERTY, Boolean.toString(true));
      }
    }
  }

  private static boolean checkFacet(final AndroidFacet facet) {
    final CheckModulePanel panel = new CheckModulePanel();
    panel.updateMessages(facet);
    final boolean hasError = panel.hasError();
    if (hasError || panel.hasWarnings()) {
      DialogWrapper dialog = new DialogWrapper(facet.getModule().getProject()) {
        {
          if (!hasError) {
            setOKButtonText("Continue");
          }
          init();
        }

        @Override
        protected Action[] createActions() {
          if (hasError) {
            return new Action[]{getOKAction()};
          }
          return super.createActions();
        }

        @Override
        protected JComponent createCenterPanel() {
          return panel;
        }
      };
      dialog.setTitle(hasError ? CommonBundle.getErrorTitle() : CommonBundle.getWarningTitle());
      dialog.show();
      return !hasError && dialog.isOK();
    }
    return true;
  }
}
