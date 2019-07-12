// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.PublicFieldBasedOptionDescription;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationSettings;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.vcs.commit.message.CommitMessageInspectionProfile.getBodyRightMargin;

/**
 * @author Sergey.Malenkov
 */
public final class VcsOptionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @NotNull
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    if (ProjectLevelVcsManager.getInstance(project).getAllVcss().length == 0) {
      return Collections.emptyList();
    }

    VcsConfiguration vcs = VcsConfiguration.getInstance(project);
    if (vcs == null) {
      return Collections.emptyList();
    }
    ArrayList<BooleanOptionDescription> options = new ArrayList<>();

    String id = "project.propVCSSupport.Mappings"; // process Version Control settings
    options.add(option(vcs, id, "Version Control: Limit history by " + vcs.MAXIMUM_HISTORY_ROWS + " rows", "LIMIT_HISTORY"));
    options.add(option(vcs, id, "Version Control: " + VcsBundle.message("checkbox.show.dirty.recursively"), "SHOW_DIRTY_RECURSIVELY"));

    VcsContentAnnotationSettings vcsCA = VcsContentAnnotationSettings.getInstance(project);
    if (vcsCA != null) {
      options.add(option(vcsCA, id, "Version Control: Show changed in last " + vcsCA.getLimitDays() + " days", "isShow", "setShow"));
    }
    options.add(option(vcs, id, "Commit message: Show right margin at " + getBodyRightMargin(project) + " columns", "USE_COMMIT_MESSAGE_MARGIN"));
    options.add(option(vcs, id, "Commit message: " + ApplicationBundle.message("checkbox.wrap.typing.on.right.margin"), "WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN"));

    id = "project.propVCSSupport.Confirmation"; // process Version Control / Confirmation settings
    ReadonlyStatusHandler vcsROSH = ReadonlyStatusHandler.getInstance(project);
    if (vcsROSH instanceof ReadonlyStatusHandlerImpl) {
      options.add(option(((ReadonlyStatusHandlerImpl)vcsROSH).getState(), id, VcsBundle.message("checkbox.show.clear.read.only.status.dialog"), "SHOW_DIALOG"));
    }
    options.add(option(vcs, id, "Confirmation: " + VcsBundle.message("checkbox.changelist.move.offer"), "OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT"));
    options.add(option(vcs, id, "Confirmation: " + VcsBundle.message("checkbox.force.non.empty.messages" ), "FORCE_NON_EMPTY_COMMENT"));
    options.add(option(vcs, id, "Confirmation: " + VcsBundle.message("checkbox.clear.initial.commit.message" ), "CLEAR_INITIAL_COMMIT_MESSAGE"));

    id = ShelfProjectConfigurable.HELP_ID;
    options.add(option(vcs, id, VcsBundle.message("vcs.shelf.store.base.content"), "INCLUDE_TEXT_INTO_SHELF"));

    if (!project.isDefault()) {
      // process Version Control / Changelist Conflicts settings
      options.add(tracker(project, "Changelists: " + VcsBundle.message("settings.show.conflict.resolve.dialog.checkbox"), "SHOW_DIALOG"));
      options.add(tracker(project, "Changelists: " + VcsBundle.message("settings.highlight.files.with.conflicts.checkbox"), "HIGHLIGHT_CONFLICTS"));
      options.add(tracker(project, "Changelists: " + VcsBundle.message("settings.highlight.files.from.non.active.changelist.checkbox"), "HIGHLIGHT_NON_ACTIVE_CHANGELIST"));
    }
    return Collections.unmodifiableCollection(options);
  }

  private static BooleanOptionDescription option(final Object instance, String id, String option, String field) {
    return new PublicFieldBasedOptionDescription(option, id, field) {
      @Override
      public Object getInstance() {
        return instance;
      }
    };
  }

  private static BooleanOptionDescription option(final Object instance, String id, String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, id, getter, setter) {
      @Override
      public Object getInstance() {
        return instance;
      }
    };
  }

  private static BooleanOptionDescription tracker(final Project project, String option, String field) {
    return new PublicFieldBasedOptionDescription(option, "project.propVCSSupport.ChangelistConflict", field) {
      @Override
      public Object getInstance() {
        return ChangeListManagerImpl.getInstanceImpl(project).getConflictTracker().getOptions();
      }

      @Override
      protected void fireUpdated() {
        ChangeListManagerImpl.getInstanceImpl(project).getConflictTracker().optionsChanged();
      }
    };
  }
}
