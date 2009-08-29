package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class DebuggerSettingsPanelProvider {

  public abstract int getPriority();

  public abstract Collection<? extends Configurable> getConfigurables(final Project project);

  public void apply() {
  }

  @Nullable
  public Configurable getRootConfigurable() {
    return null;
  }

  public boolean hasAnySettingsPanels() {
    return true;
  }
}
