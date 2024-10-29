// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.options.ConfigurableBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class QuickListsPanel extends ConfigurableBase<QuickListsUi, List<QuickList>> {
  public QuickListsPanel() {
    super("reference.idesettings.quicklists", IdeBundle.message("quick.lists.presentable.name"), "reference.idesettings.quicklists");
  }

  @Override
  protected @NotNull List<QuickList> getSettings() {
    return QuickListsManager.getInstance().getSchemeManager().getAllSchemes();
  }

  @Override
  protected QuickListsUi createUi() {
    return new QuickListsUi();
  }
}
