package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.options.colors.AttributesDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 3:59 PM
 */
public class IntellijLocalSyncTreeFilterAction extends GradleAbstractSyncTreeFilterAction {

  public IntellijLocalSyncTreeFilterAction() {
    super(GradleColorAndFontDescriptorsProvider.INTELLIJ_LOCAL);
  }
}
