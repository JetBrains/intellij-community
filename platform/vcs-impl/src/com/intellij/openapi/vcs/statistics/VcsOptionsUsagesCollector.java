/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.statistics;

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getBooleanUsage;
import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getEnumUsage;

public class VcsOptionsUsagesCollector extends AbstractProjectsUsagesCollector {
  private static final String GROUP_ID = "vcs-options";

  @NotNull
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }

  @NotNull
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
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
    usages.add(getBooleanUsage("commit.before.check.files.up.to.date", configuration.CHECK_FILES_UP_TO_DATE_BEFORE_COMMIT));
    usages.add(getBooleanUsage("commit.before.reformat.project", configuration.REFORMAT_BEFORE_PROJECT_COMMIT));
    usages.add(getBooleanUsage("commit.before.reformat.file", configuration.REFORMAT_BEFORE_FILE_COMMIT));
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
