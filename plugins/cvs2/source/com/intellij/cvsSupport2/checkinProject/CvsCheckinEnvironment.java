package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.RevisionsFactory;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.SystemProperties;

/**
 * author: lesya
 */
public class CvsCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;

  public boolean showCheckinDialogInAnyCase() {
    return false;
  }

  public CvsCheckinEnvironment(Project project) {
    myProject = project;
  }

  public RevisionsFactory getRevisionsFactory() {
    return new CvsRevisionsFactory(myProject);
  }


  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(final Refreshable panel) {
    return null;
    // TODO: shall these options be available elsewhere?
    /*return new CvsProjectAdditionalPanel(panel, myProject);*/
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, final boolean checkinProject) {
    return null;
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinFile(Refreshable panel) {
    return null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    if (filesToCheckin == null) {
      return null;
    }
    if (filesToCheckin.length != 1) {
      return null;
    }
    return CvsUtil.getTemplateFor(filesToCheckin[0]);
  }

  public void onRefreshFinished() {
    CvsEntriesManager.getInstance().unlockSynchronizationActions();
  }

  public void onRefreshStarted() {
    CvsEntriesManager.getInstance().lockSynchronizationActions();
  }

  public String prepareCheckinMessage(String text) {
    if (text == null) return null;
    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    StringBuffer buffer = new StringBuffer();
    boolean firstLine = true;
    for (String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!line.startsWith("CVS:")) {
        if (!firstLine) buffer.append(SystemProperties.getLineSeparator());
        buffer.append(line);
        firstLine = false;
      }
    }
    return buffer.toString();
  }

  public String getHelpId() {
    return "cvs.commitProject";
  }

  public String getCheckinOperationName() {
    return com.intellij.CvsBundle.message("operation.name.checkin.project");
  }

}
