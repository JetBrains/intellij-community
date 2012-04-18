package org.jetbrains.plugins.gradle.action;

import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 6:08 PM
 */
public class GradleConflictSyncTreeFilterAction extends AbstractGradleSyncTreeFilterAction {

  public GradleConflictSyncTreeFilterAction() {
    super(GradleColorAndFontDescriptorsProvider.CONFLICT);
  }
}
