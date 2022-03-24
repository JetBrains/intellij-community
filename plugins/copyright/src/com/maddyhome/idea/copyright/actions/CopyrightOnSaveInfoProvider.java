// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.actions;

import com.intellij.codeInsight.actions.onSave.ActionOnSaveInfoBase;
import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.ide.actionsOnSave.ActionOnSaveComment;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.ActionLink;
import com.maddyhome.idea.copyright.ui.CopyrightProjectConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class CopyrightOnSaveInfoProvider extends ActionOnSaveInfoProvider {

  private static final String UPDATE_COPYRIGHT_ON_SAVE = "update.copyright.on.save";
  private static final boolean UPDATE_COPYRIGHT_BY_DEFAULT = false;

  public static boolean isUpdateCopyrightOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(UPDATE_COPYRIGHT_ON_SAVE, UPDATE_COPYRIGHT_BY_DEFAULT);
  }

  @Override
  protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
    return List.of(new ActionOnSaveInfoBase(context, CopyrightBundle.message("checkbox.update.copyright.notice"), UPDATE_COPYRIGHT_ON_SAVE, UPDATE_COPYRIGHT_BY_DEFAULT) {
      @Override
      public @NotNull List<? extends ActionLink> getActionLinks() {
        return List.of(createGoToPageInSettingsLink(CopyrightBundle.message("link.label.configure.copyright.settings"),
                                                    CopyrightProjectConfigurable.ID));
      }

      private boolean hasCopyrights() {
        Configurable configurable = getSettings().getConfigurableWithInitializedUiComponent(CopyrightProjectConfigurable.ID, false);
        CopyrightProjectConfigurable copyrightProjectConfigurable = ConfigurableWrapper.cast(CopyrightProjectConfigurable.class, configurable);
        if (copyrightProjectConfigurable != null) {
          return copyrightProjectConfigurable.hasAnyCopyrights();
        }
        return CopyrightManager.getInstance(context.getProject()).hasAnyCopyrights();
      }

      @Override
      public boolean isSaveActionApplicable() {
        return hasCopyrights();
      }

      @Override
      public ActionOnSaveComment getComment() {
        return ActionOnSaveComment.info(CopyrightBundle.message(hasCopyrights()
                                                                ? "label.updates.existing.copyrights.e.g.changes.year.or.updated.notice"
                                                                : "label.no.copyright.configured"));
      }
    });
  }

  @Override
  public Collection<String> getSearchableOptions() {
    return List.of(CopyrightBundle.message("checkbox.update.copyright.notice"));
  }
}
