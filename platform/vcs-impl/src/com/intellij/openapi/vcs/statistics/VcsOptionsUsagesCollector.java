// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.addIfDiffers;

public class VcsOptionsUsagesCollector extends ProjectUsagesCollector {
  @Override
  @NotNull
  public String getGroupId() { return "statistics.vcs.options"; }

  @Override
  @NotNull
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors(@NotNull Project project) {
    Set<UsageDescriptor> set = new HashSet<>();

    VcsConfiguration conf = VcsConfiguration.getInstance(project);
    VcsConfiguration confDefault = new VcsConfiguration();

    addBoolIfDiffers(set, conf, confDefault, s -> s.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT, "offer.move.partially.committed");
    addConfirmationIfDiffers(set, conf, confDefault, s -> s.MOVE_TO_FAILED_COMMIT_CHANGELIST, "offer.move.failed.committed");
    addConfirmationIfDiffers(set, conf, confDefault, s -> s.REMOVE_EMPTY_INACTIVE_CHANGELISTS, "offer.remove.empty.changelist");

    addBoolIfDiffers(set, conf, confDefault, s -> s.MAKE_NEW_CHANGELIST_ACTIVE, "changelist.make.new.active");
    addBoolIfDiffers(set, conf, confDefault, s -> s.PRESELECT_EXISTING_CHANGELIST, "changelist.preselect.existing");

    addBoolIfDiffers(set, conf, confDefault, s -> s.PERFORM_UPDATE_IN_BACKGROUND, "perform.update.in.background");
    addBoolIfDiffers(set, conf, confDefault, s -> s.PERFORM_COMMIT_IN_BACKGROUND, "perform.commit.in.background");
    addBoolIfDiffers(set, conf, confDefault, s -> s.PERFORM_EDIT_IN_BACKGROUND, "perform.edit.in.background");
    addBoolIfDiffers(set, conf, confDefault, s -> s.PERFORM_CHECKOUT_IN_BACKGROUND, "perform.checkout.in.background");
    addBoolIfDiffers(set, conf, confDefault, s -> s.PERFORM_ADD_REMOVE_IN_BACKGROUND, "perform.add_remove.in.background");
    addBoolIfDiffers(set, conf, confDefault, s -> s.PERFORM_ROLLBACK_IN_BACKGROUND, "perform.rollback.in.background");

    addBoolIfDiffers(set, conf, confDefault, s -> s.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT, "commit.before.check.code.smell");
    addBoolIfDiffers(set, conf, confDefault, s -> s.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT, "commit.before.check.code.cleanup");
    addBoolIfDiffers(set, conf, confDefault, s -> s.CHECK_NEW_TODO, "commit.before.check.todo");
    addBoolIfDiffers(set, conf, confDefault, s -> s.FORCE_NON_EMPTY_COMMENT, "commit.before.check.non.empty.comment");
    addBoolIfDiffers(set, conf, confDefault, s -> s.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT, "commit.before.optimize.imports");
    addBoolIfDiffers(set, conf, confDefault, s -> s.REFORMAT_BEFORE_PROJECT_COMMIT, "commit.before.reformat.project");
    addBoolIfDiffers(set, conf, confDefault, s -> s.REARRANGE_BEFORE_PROJECT_COMMIT, "commit.before.rearrange");

    addBoolIfDiffers(set, conf, confDefault, s -> s.CLEAR_INITIAL_COMMIT_MESSAGE, "commit.clear.initial.comment");
    addBoolIfDiffers(set, conf, confDefault, s -> s.USE_COMMIT_MESSAGE_MARGIN, "commit.use.right.margin");
    addBoolIfDiffers(set, conf, confDefault, s -> s.SHOW_UNVERSIONED_FILES_WHILE_COMMIT, "commit.show.unversioned");

    addBoolIfDiffers(set, conf, confDefault, s -> s.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN, "show.changes.preview");
    addBoolIfDiffers(set, conf, confDefault, s -> s.INCLUDE_TEXT_INTO_SHELF, "include.text.into.shelf");
    addBoolIfDiffers(set, conf, confDefault, s -> s.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND, "check.conflicts.in.background");

    return set;
  }

  private static <T> void addBoolIfDiffers(Set<? super UsageDescriptor> set, T settingsBean, T defaultSettingsBean,
                                           Function1<T, Boolean> valueFunction, String featureId) {
    addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, (it) -> it ? featureId : featureId + ".disabled");
  }

  private static <T> void addConfirmationIfDiffers(Set<? super UsageDescriptor> set, T settingsBean, T defaultSettingsBean,
                                                   Function1<T, VcsShowConfirmationOption.Value> valueFunction, String featureId) {
    addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, (it) -> {
      switch (it) {
        case SHOW_CONFIRMATION:
          return featureId + ".ask";
        case DO_NOTHING_SILENTLY:
          return featureId + ".disabled";
        case DO_ACTION_SILENTLY:
          return featureId + ".silently";
        default:
          return featureId + ".unknown";
      }
    });
  }
}
