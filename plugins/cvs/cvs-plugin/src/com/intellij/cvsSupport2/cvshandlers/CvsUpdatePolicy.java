// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.CvsBundle;
import org.jetbrains.annotations.NonNls;

public final class CvsUpdatePolicy {
  @NonNls public static final String BINARY_MERGED_ID = "BINARY_MERGED";
  @NonNls public static final String UNKNOWN_TYPE_ID = "UNKNOWN_TYPE";
  @NonNls public static final String MODIFIED_REMOVED_FROM_SERVER_ID = "MOD_REMOVED_FROM_SERVER";
  @NonNls public static final String LOCALLY_REMOVED_MODIFIED_ON_SERVER_ID = "LOCALLY_REMOVED_MODIFIED_ON_SERVER";
  @NonNls public static final String CREATED_BY_SECOND_PARTY_ID = "CREATED_BY_SECOND_PARTY";

  private CvsUpdatePolicy() {
  }

  public static UpdatedFiles createUpdatedFiles() {
    UpdatedFiles result = UpdatedFiles.create();

    fillGroups(result);

    return result;
  }

  public static void fillGroups(UpdatedFiles result) {
    result.registerGroup(new FileGroup(CvsBundle.message("update.status.unknown"), CvsBundle.message("update.status.unknown"), false, CvsUpdatePolicy.UNKNOWN_TYPE_ID, false));
    result.registerGroup(
      new FileGroup(CvsBundle.message("update.status.locally.modified.removed.from.server"), CvsBundle.message("update.status.locally.modified.removed.from.server"),false, CvsUpdatePolicy.MODIFIED_REMOVED_FROM_SERVER_ID, false));
    result.registerGroup(
      new FileGroup(CvsBundle.message("update.status.locally.removed.modified.on.server"), CvsBundle.message("update.status.locally.removed.modified.on.server"), false, CvsUpdatePolicy.LOCALLY_REMOVED_MODIFIED_ON_SERVER_ID, false));
    result.registerGroup(new FileGroup(CvsBundle.message("update.status.created.by.second.party"), CvsBundle.message("update.status.created.by.second.party"), true, CvsUpdatePolicy.CREATED_BY_SECOND_PARTY_ID, false));
    result.registerGroup(new FileGroup(CvsBundle.message("update.status.binary.file.has.to.be.merged"), CvsBundle.message("update.status.binary.file.has.to.be.merged"), false, CvsUpdatePolicy.BINARY_MERGED_ID, false));
  }
}
