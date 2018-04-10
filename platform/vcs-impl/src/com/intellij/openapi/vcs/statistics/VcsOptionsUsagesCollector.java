// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getBooleanUsage;
import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getEnumUsage;

public class VcsOptionsUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  public String getGroupId() { return "statistics.vcs.options"; }

  @NotNull
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors(@NotNull Project project) {
    VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    Set<UsageDescriptor> usages = new HashSet<>();

    usages.add(getBooleanUsage("offer.move.partially.committed", configuration.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT));
    usages.add(getEnumUsage("offer.move.failed.committed", configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST));
    usages.add(getEnumUsage("offer.remove.empty.changelist", configuration.REMOVE_EMPTY_INACTIVE_CHANGELISTS));

    usages.add(getBooleanUsage("changelist.make.new.active", configuration.MAKE_NEW_CHANGELIST_ACTIVE));
    usages.add(getBooleanUsage("changelist.preselect.existing", configuration.PRESELECT_EXISTING_CHANGELIST));

    usages.add(getBooleanUsage("perform.update.in.background", configuration.PERFORM_UPDATE_IN_BACKGROUND));
    usages.add(getBooleanUsage("perform.commit.in.background", configuration.PERFORM_COMMIT_IN_BACKGROUND));
    usages.add(getBooleanUsage("perform.edit.in.background", configuration.PERFORM_EDIT_IN_BACKGROUND));
    usages.add(getBooleanUsage("perform.checkout.in.background", configuration.PERFORM_CHECKOUT_IN_BACKGROUND));
    usages.add(getBooleanUsage("perform.add_remove.in.background", configuration.PERFORM_ADD_REMOVE_IN_BACKGROUND));
    usages.add(getBooleanUsage("perform.rollback.in.background", configuration.PERFORM_ROLLBACK_IN_BACKGROUND));

    usages.add(getBooleanUsage("commit.before.check.code.smell", configuration.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT));
    usages.add(getBooleanUsage("commit.before.check.code.cleanup", configuration.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT));
    usages.add(getBooleanUsage("commit.before.check.todo", configuration.CHECK_NEW_TODO));
    usages.add(getBooleanUsage("commit.before.check.non.empty.comment", configuration.FORCE_NON_EMPTY_COMMENT));
    usages.add(getBooleanUsage("commit.before.optimize.imports", configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT));
    usages.add(getBooleanUsage("commit.before.reformat.project", configuration.REFORMAT_BEFORE_PROJECT_COMMIT));
    usages.add(getBooleanUsage("commit.before.rearrange", configuration.REARRANGE_BEFORE_PROJECT_COMMIT));

    usages.add(getBooleanUsage("commit.clear.initial.comment", configuration.CLEAR_INITIAL_COMMIT_MESSAGE));
    usages.add(getBooleanUsage("commit.use.right.margin", configuration.USE_COMMIT_MESSAGE_MARGIN));
    usages.add(getBooleanUsage("commit.show.unversioned", configuration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT));

    usages.add(getBooleanUsage("show.changes.preview", configuration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN));
    usages.add(getBooleanUsage("include.text.into.shelf", configuration.INCLUDE_TEXT_INTO_SHELF));
    usages.add(getBooleanUsage("check.conflicts.in.background", configuration.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND));

    return usages;
  }
}
