// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.actions;

import com.intellij.codeInsight.actions.onSave.ActionOnSaveInfoBase;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.ActionLink;
import com.maddyhome.idea.copyright.ui.CopyrightProjectConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CopyrightOnSaveInfoProvider extends ActionOnSaveInfoProvider {

  public static final String UPDATE_COPYRIGHT_ON_SAVE = "update.copyright.on.save";
  public static final boolean UPDATE_COPYRIGHT_BY_DEFAULT = false;

  public static boolean isUpdateCopyrightOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(UPDATE_COPYRIGHT_ON_SAVE, UPDATE_COPYRIGHT_BY_DEFAULT);
  }

  
  @Override
  protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
    return Collections.singletonList(new ActionOnSaveInfoBase(context, "Update copyright",
                                                              UPDATE_COPYRIGHT_ON_SAVE, UPDATE_COPYRIGHT_BY_DEFAULT) {
      @Override
      public @NotNull List<? extends ActionLink> getActionLinks() {
        return List.of(createGoToPageInSettingsLink("Configure copyright settings",
                                                    CopyrightProjectConfigurable.ID));
      }
    });
  }
}
