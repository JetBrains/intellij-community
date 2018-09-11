/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.actions.update;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public class UpdateSettingsOnCvsConfiguration implements UpdateSettings{
  private final boolean myCleanCopy;
  private final CvsConfiguration myConfiguration;
  private final boolean myResetSticky;

  public UpdateSettingsOnCvsConfiguration(final boolean pruneEmptyDirectories, final int mergingMode, final String mergeWithBranchOneName,
                                          final String mergeWithBranchTwoName, final boolean createNewDirectories,
                                          final String updateKeyboardSubstitution, final DateOrRevisionSettings updateDateOrRevisionSettings,
                                          final boolean makeNewFilesReadonly, final boolean cleanCopy, final boolean resetSticky) {
    myCleanCopy = cleanCopy;
    myResetSticky = resetSticky;

    myConfiguration = new CvsConfiguration();
    myConfiguration.PRUNE_EMPTY_DIRECTORIES = pruneEmptyDirectories;
    myConfiguration.MERGING_MODE = mergingMode;
    myConfiguration.MERGE_WITH_BRANCH1_NAME = mergeWithBranchOneName;
    myConfiguration.MERGE_WITH_BRANCH2_NAME = mergeWithBranchTwoName;
    myConfiguration.CREATE_NEW_DIRECTORIES = createNewDirectories;
    myConfiguration.UPDATE_KEYWORD_SUBSTITUTION = updateKeyboardSubstitution;
    myConfiguration.UPDATE_DATE_OR_REVISION_SETTINGS = updateDateOrRevisionSettings;
    myConfiguration.MAKE_NEW_FILES_READONLY = makeNewFilesReadonly;
  }

  public UpdateSettingsOnCvsConfiguration(CvsConfiguration configuration, boolean cleanCopy, boolean resetSticky) {
    myCleanCopy = cleanCopy;
    myConfiguration = configuration;
    myResetSticky = resetSticky;
  }

  @Override
  public boolean getPruneEmptyDirectories() {
    return myConfiguration.PRUNE_EMPTY_DIRECTORIES;
  }

  @Override
  public String getBranch1ToMergeWith() {
    if (myConfiguration.MERGING_MODE == CvsConfiguration.DO_NOT_MERGE) return null;
    return myConfiguration.MERGE_WITH_BRANCH1_NAME;
  }

  @Override
  public String getBranch2ToMergeWith() {
    if (myConfiguration.MERGING_MODE == CvsConfiguration.DO_NOT_MERGE) return null;
    if (myConfiguration.MERGING_MODE == CvsConfiguration.MERGE_WITH_BRANCH) return null;
    return myConfiguration.MERGE_WITH_BRANCH2_NAME;
  }

  @Override
  public boolean getResetAllSticky() {
    return myResetSticky;
  }

  @Override
  public boolean getDontMakeAnyChanges() {
    return false;
  }

  @Override
  public boolean getCreateDirectories() {
    return myConfiguration.CREATE_NEW_DIRECTORIES;
  }

  @Override
  public boolean getCleanCopy() {
    return myCleanCopy;
  }

  @Override
  public KeywordSubstitution getKeywordSubstitution() {
    KeywordSubstitutionWrapper value = KeywordSubstitutionWrapper.getValue(myConfiguration.UPDATE_KEYWORD_SUBSTITUTION);
    if (value == null) return null;
    return value.getSubstitution();
  }

  @Override
  public RevisionOrDate getRevisionOrDate() {
    return RevisionOrDateImpl.createOn(myConfiguration.UPDATE_DATE_OR_REVISION_SETTINGS);
  }

  @Override
  public boolean getMakeNewFilesReadOnly() {
    return myConfiguration.MAKE_NEW_FILES_READONLY;
  }
}
