
/*
 * User: anna
 * Date: 08-Dec-2008
 */
package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;

public class UpdateCopyrightCheckinHandlerFactory extends CheckinHandlerFactory {
  @NotNull
  public CheckinHandler createHandler(CheckinProjectPanel panel) {
    return new UpdateCopyrightCheckinHandler(panel);
  }
}