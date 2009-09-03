/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2;

import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.cvsSupport2.checkinProject.AdditionalOptionsPanel;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
*/
class CvsCheckinHandlerFactory extends CheckinHandlerFactory {
  @NotNull
  public CheckinHandler createHandler(final CheckinProjectPanel panel) {
    return new CheckinHandler() {
      @Nullable
      public RefreshableOnComponent getAfterCheckinConfigurationPanel() {
        if (panel.getAffectedVcses().contains(CvsVcs2.getInstance(panel.getProject()))) {
          return new AdditionalOptionsPanel(true, CvsConfiguration.getInstance(panel.getProject()));
        }
        else {
          return null;
        }
      }
    };
  }
}