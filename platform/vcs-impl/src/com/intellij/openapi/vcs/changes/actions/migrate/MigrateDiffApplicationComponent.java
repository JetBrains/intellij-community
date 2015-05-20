package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diff.DiffManager;
import org.jetbrains.annotations.NotNull;

public class MigrateDiffApplicationComponent implements ApplicationComponent {
  @Override
  public void initComponent() {
    DiffManager.getInstance().registerDiffTool(MigrateDiffTool.INSTANCE);
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "MigrateDiffApplicationComponent";
  }
}
