package org.jetbrains.android.actions;

import com.android.sdklib.SdkConstants;
import com.intellij.CommonBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class RunAndroidSdkManagerAction extends AnAction {
  public RunAndroidSdkManagerAction() {
    super(AndroidBundle.message("android.run.sdk.manager.action.text"));
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    assert project != null;
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    assert facets.size() > 0;
    Set<String> sdkSet = new HashSet<String>();
    for (AndroidFacet facet : facets) {
      AndroidSdk sdk = facet.getConfiguration().getAndroidSdk();
      if (sdk != null) {
        sdkSet.add(sdk.getLocation());
      }
    }
    if (sdkSet.size() == 0) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
      return;
    }
    String sdkPath = sdkSet.iterator().next();
    if (sdkSet.size() > 1) {
      String[] sdks = ArrayUtil.toStringArray(sdkSet);
      int index = Messages.showChooseDialog(project, AndroidBundle.message("android.choose.sdk.label"),
                                            AndroidBundle.message("android.choose.sdk.title"),
                                            Messages.getQuestionIcon(), sdks, sdkPath);
      if (index < 0) {
        return;
      }
      sdkPath = sdks[index];
    }
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(sdkPath + File.separator + AndroidUtils.toolPath(SdkConstants.androidCmdName()));
    AndroidUtils.runExternalToolInSeparateThread(project, commandLine, null);
  }
}
