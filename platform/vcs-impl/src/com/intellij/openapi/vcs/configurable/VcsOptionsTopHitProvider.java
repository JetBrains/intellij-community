/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicFieldBasedOptionDescription;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationSettings;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class VcsOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    if (project == null || ProjectLevelVcsManager.getInstance(project).getAllVcss().length == 0) {
      return Collections.emptyList();
    }
    VcsConfiguration vcs = VcsConfiguration.getInstance(project);
    if (vcs == null) {
      return Collections.emptyList();
    }
    ArrayList<BooleanOptionDescription> options = new ArrayList<>();

    String id = "project.propVCSSupport.Mappings"; // process Version Control settings
    options.add(option(vcs, id, "Limit history by " + vcs.MAXIMUM_HISTORY_ROWS + " rows", "LIMIT_HISTORY"));
    options.add(option(vcs, id, "Show directories with changed descendants", "SHOW_DIRTY_RECURSIVELY"));
    options.add(option(vcs, id, "Store on shelf base revision texts for files under DVCS", "INCLUDE_TEXT_INTO_SHELF"));
    VcsContentAnnotationSettings vcsCA = VcsContentAnnotationSettings.getInstance(project);
    if (vcsCA != null) {
      options.add(option(vcsCA, id, "Show changed in last " + vcsCA.getLimitDays() + " days", "isShow", "setShow"));
    }
    options.add(option(vcs, id, "Notify about VCS root errors", "SHOW_VCS_ERROR_NOTIFICATIONS"));
    options.add(option(vcs, id, "Commit message right margin " + vcs.COMMIT_MESSAGE_MARGIN_SIZE + " columns", "USE_COMMIT_MESSAGE_MARGIN"));
    options.add(option(vcs, id, ApplicationBundle.message("checkbox.wrap.typing.on.right.margin"), "WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN"));

    id = "project.propVCSSupport.Confirmation"; // process Version Control / Confirmation settings
    ReadonlyStatusHandler vcsROSH = ReadonlyStatusHandler.getInstance(project);
    if (vcsROSH instanceof ReadonlyStatusHandlerImpl) {
      options.add(option(((ReadonlyStatusHandlerImpl)vcsROSH).getState(), id, "Show \"Clear Read-only Status\" Dialog", "SHOW_DIALOG"));
    }
    options.add(option(vcs, id, "Confirmation: Suggest to move uncommitted changes to another changelist", "OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT"));
    options.add(option(vcs, id, "Confirmation: Force non-empty checkin comments", "FORCE_NON_EMPTY_COMMENT"));
    options.add(option(vcs, id, "Confirmation: Clear initial commit message", "CLEAR_INITIAL_COMMIT_MESSAGE"));

    id = "project.propVCSSupport.Background"; // process Version Control / Background settings
    options.add(option(vcs, id, "Perform in background: update from VCS", "PERFORM_UPDATE_IN_BACKGROUND"));
    options.add(option(vcs, id, "Perform in background: commit to VCS", "PERFORM_COMMIT_IN_BACKGROUND"));
    options.add(option(vcs, id, "Perform in background: checkout from VCS", "PERFORM_CHECKOUT_IN_BACKGROUND"));
    options.add(option(vcs, id, "Perform in background: Edit/Checkout", "PERFORM_EDIT_IN_BACKGROUND"));
    options.add(option(vcs, id, "Perform in background: Add/Remove", "PERFORM_ADD_REMOVE_IN_BACKGROUND"));
    options.add(option(vcs, id, "Perform in background: revert", "PERFORM_ROLLBACK_IN_BACKGROUND"));

    if (!project.isDefault()) {
      // process Version Control / Changelist Conflicts settings
      options.add(tracker(project, "Changelists: Enable changelist conflict tracking", "TRACKING_ENABLED"));
      options.add(tracker(project, "Changelists: Show conflict resolving dialog", "SHOW_DIALOG"));
      options.add(tracker(project, "Changelists: Highlight files with conflicts", "HIGHLIGHT_CONFLICTS"));
      options.add(tracker(project, "Changelists: Highlight files from non-active changelists", "HIGHLIGHT_NON_ACTIVE_CHANGELIST"));
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
