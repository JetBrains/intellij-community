package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.actions.VcsContext;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class ConfigureCvsRootsAction extends CvsGlobalAction {

  public void actionPerformed(AnActionEvent e) {
    VcsContext cvsContext = CvsContextWrapper.createCachedInstance(e);
    CvsApplicationLevelConfiguration configuration = CvsApplicationLevelConfiguration.getInstance();
    List<CvsRootConfiguration> configurations = configuration.CONFIGURATIONS;
    CvsConfigurationsListEditor cvsConfigurationsListEditor =
      new CvsConfigurationsListEditor(new ArrayList<CvsRootConfiguration>(configurations), cvsContext.getProject());
    cvsConfigurationsListEditor.show();
    if (cvsConfigurationsListEditor.isOK()) {
      configuration.CONFIGURATIONS = cvsConfigurationsListEditor.getConfigurations();
    }

  }
}
