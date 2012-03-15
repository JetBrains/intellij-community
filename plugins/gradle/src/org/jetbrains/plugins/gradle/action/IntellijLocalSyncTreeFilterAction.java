package org.jetbrains.plugins.gradle.action;

import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 3:59 PM
 */
public class IntellijLocalSyncTreeFilterAction extends AbstractGradleSyncTreeFilterAction {

  public IntellijLocalSyncTreeFilterAction() {
    super(GradleColorAndFontDescriptorsProvider.INTELLIJ_LOCAL);
  }
}
