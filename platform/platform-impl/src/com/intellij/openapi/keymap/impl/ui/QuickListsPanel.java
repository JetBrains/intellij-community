/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.options.ConfigurableBase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class QuickListsPanel extends ConfigurableBase<QuickListsUi, List<QuickList>> {
  public QuickListsPanel() {
    super("reference.idesettings.quicklists", "Quick Lists", "reference.idesettings.quicklists");
  }

  @NotNull
  @Override
  protected List<QuickList> getSettings() {
    return QuickListsManager.getInstance().getSchemeManager().getAllSchemes();
  }

  @Override
  protected QuickListsUi createUi() {
    return new QuickListsUi();
  }
}
