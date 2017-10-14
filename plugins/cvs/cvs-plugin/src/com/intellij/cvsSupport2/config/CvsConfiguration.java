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
package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.util.Options;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.netbeans.lib.cvsclient.command.Watch;

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */

@State(name = "Cvs2Configuration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class CvsConfiguration implements PersistentStateComponent<CvsConfiguration> {

  public static final int DO_NOT_MERGE = 0;
  public static final int MERGE_WITH_BRANCH = 1;
  public static final int MERGE_TWO_BRANCHES = 2;


  public boolean PRUNE_EMPTY_DIRECTORIES = true;

  public int MERGING_MODE = DO_NOT_MERGE;
  public String MERGE_WITH_BRANCH1_NAME = CvsUtil.HEAD;
  public String MERGE_WITH_BRANCH2_NAME = CvsUtil.HEAD;
  public boolean RESET_STICKY;
  public boolean CREATE_NEW_DIRECTORIES = true;
  public String DEFAULT_TEXT_FILE_SUBSTITUTION = KeywordSubstitutionWrapper.KEYWORD_EXPANSION.getSubstitution().toString();
  
  public boolean PROCESS_UNKNOWN_FILES;
  public boolean PROCESS_DELETED_FILES;
  public boolean PROCESS_IGNORED_FILES;

  public boolean RESERVED_EDIT;
  public DateOrRevisionSettings CHECKOUT_DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();
  public DateOrRevisionSettings UPDATE_DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();
  public DateOrRevisionSettings SHOW_CHANGES_REVISION_SETTINGS = new DateOrRevisionSettings();
  public boolean SHOW_OUTPUT;
  public int ADD_WATCH_INDEX;
  public List<Watch> WATCHERS = Arrays.asList(Watch.ALL, Watch.EDIT, Watch.UNEDIT, Watch.COMMIT);
  public int REMOVE_WATCH_INDEX;
  public String UPDATE_KEYWORD_SUBSTITUTION;

  public boolean MAKE_NEW_FILES_READONLY;

  @Options.Values
  public int SHOW_CORRUPTED_PROJECT_FILES = Options.SHOW_DIALOG;

  public boolean TAG_AFTER_PROJECT_COMMIT;
  public boolean OVERRIDE_EXISTING_TAG_FOR_PROJECT = true;
  public String TAG_AFTER_PROJECT_COMMIT_NAME = "";
  public boolean CLEAN_COPY;


  public static CvsConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, CvsConfiguration.class);
  }

  public static VcsShowConfirmationOption.Value convertToEnumValue(boolean value, boolean onOk) {
    if (value) {
      return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }
    else if (onOk) {
      return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
    }
    else {
      return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
    }
  }

  @Override
  public CvsConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(CvsConfiguration object) {
    XmlSerializerUtil.copyBean(object, this);
    // safeguard (IDEADEV-15053)
    if (CHECKOUT_DATE_OR_REVISION_SETTINGS == null) {
      CHECKOUT_DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();
    }
    if (UPDATE_DATE_OR_REVISION_SETTINGS == null) {
      UPDATE_DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();
    }
    if (SHOW_CHANGES_REVISION_SETTINGS == null) {
      SHOW_CHANGES_REVISION_SETTINGS = new DateOrRevisionSettings();
    }
  }
}
