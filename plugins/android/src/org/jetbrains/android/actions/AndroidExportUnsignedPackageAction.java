package org.jetbrains.android.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExportUnsignedPackageAction extends AnAction {

  public AndroidExportUnsignedPackageAction() {
    super(AndroidBundle.message("android.export.unsigned.package.action.text"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    List<AndroidFacet> facets = AndroidUtils.getApplicationFacets(project);
    assert facets.size() > 0;
    ExportSignedPackageWizard wizard = new ExportSignedPackageWizard(project, facets, false);
    wizard.show();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && AndroidUtils.getApplicationFacets(project).size() > 0);
  }
}
