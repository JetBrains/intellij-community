package org.jetbrains.plugins.gradle.action;

import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 3:54 PM
 */
public class GradleLocalSyncTreeFilterAction extends AbstractGradleSyncTreeFilterAction {

  public GradleLocalSyncTreeFilterAction() {
    super(GradleColorAndFontDescriptorsProvider.GRADLE_LOCAL);
  }
}
