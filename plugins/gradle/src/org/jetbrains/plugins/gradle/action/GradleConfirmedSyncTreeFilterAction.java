package org.jetbrains.plugins.gradle.action;

import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 6:07 PM
 */
public class GradleConfirmedSyncTreeFilterAction extends AbstractGradleSyncTreeFilterAction {

  public GradleConfirmedSyncTreeFilterAction() {
    super(GradleColorAndFontDescriptorsProvider.CONFIRMED);
  }
}
