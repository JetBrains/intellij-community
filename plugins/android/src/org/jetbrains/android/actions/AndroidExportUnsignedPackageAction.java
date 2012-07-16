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
public class AndroidExportUnsignedPackageAction extends AnAction {

  private static final String ANDROID_HIDE_UNSIGNED_EXPORT = "ANDROID_HIDE_UNSIGNED_EXPORT";

  public AndroidExportUnsignedPackageAction() {
    super(AndroidBundle.message("android.export.unsigned.package.action.text"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final String doNotShowStr = PropertiesComponent.getInstance().getValue(ANDROID_HIDE_UNSIGNED_EXPORT);
    final boolean doNotShow = Boolean.parseBoolean(doNotShowStr);

    if (!doNotShow) {
      final boolean[] hide = {false};
      final int result = Messages.showCheckboxMessageDialog("There is no 'Export Unsigned Application Package' wizard since IDEA 12.\n" +
                                                            "Instead, please open 'File | Project Structure | Artifacts' and create new 'Android Application' artifact.",
                                                            "Export Unsigned Package",
                                                            new String[]{CommonBundle.getOkButtonText()},
                                                            "Hide 'Export Unsigned Application Package' in the menu", false, 0, 0,
                                                            Messages.getInformationIcon(),
                                                            new PairFunction<Integer, JCheckBox, Integer>() {
                                                              @Override
                                                              public Integer fun(Integer integer, JCheckBox checkBox) {
                                                                hide[0] = checkBox.isSelected();
                                                                return integer;
                                                              }
                                                            });

      if (result == Messages.OK && hide[0]) {
        PropertiesComponent.getInstance().setValue(ANDROID_HIDE_UNSIGNED_EXPORT, Boolean.toString(true));
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && AndroidUtils.getApplicationFacets(project).size() > 0);

    final String hide = PropertiesComponent.getInstance().getValue(ANDROID_HIDE_UNSIGNED_EXPORT);
    if (Boolean.parseBoolean(hide)) {
      e.getPresentation().setVisible(false);
    }
  }
}
