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
package com.intellij.cvsSupport2.ui;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;

public interface Options {
  boolean isToBeShown(Project project);

  void setToBeShown(boolean value, Project project, boolean onOk);

  Options ADD_ACTION = new Options() {
    @Override
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getAddOptions().getValue();
    }

    @Override
    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getAddOptions().setValue(value);
    }
  };

  Options ON_FILE_ADDING = new Options() {
    @Override
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getAddConfirmation().getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }

    @Override
    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getAddConfirmation().setValue(CvsConfiguration.convertToEnumValue(value, onOk));
    }

  };


  Options ON_FILE_REMOVING = new Options() {
    @Override
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getRemoveConfirmation().getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }

    @Override
    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getRemoveConfirmation().setValue(CvsConfiguration.convertToEnumValue(value, onOk));
    }
  };

  Options REMOVE_ACTION = new Options() {
    @Override
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getRemoveOptions().getValue();
    }

    @Override
    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getRemoveOptions().setValue(value);
    }
  };

  Options NULL = new Options() {
    @Override
    public boolean isToBeShown(Project project) { return true; }
    @Override
    public void setToBeShown(boolean value, Project project, boolean onOk) {}
  };
}

