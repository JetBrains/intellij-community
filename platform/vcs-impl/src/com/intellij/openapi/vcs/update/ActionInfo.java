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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;

import java.util.LinkedHashMap;

public interface ActionInfo {  
  ActionInfo UPDATE = new ActionInfo() {
    @Override
    public boolean showOptions(Project project) {
      return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).getValue();
    }

    @Override
    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getUpdateEnvironment();
    }

    @Override
    public String getActionName() {
      return VcsBundle.message("action.name.update");
    }

    @Override
    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                           final String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        @Override
        protected String getRealTitle() {
          return VcsBundle.message("action.display.name.update.scope", scopeName);
        }

        @Override
        protected String getActionNameForDimensions() {
          return "update";
        }

        @Override
        protected boolean isToBeShown() {
          return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).getValue();
        }

        @Override
        protected void setToBeShown(boolean value, boolean onOk) {
          if (onOk) {
            ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).setValue(value);
          }
        }
      };
    }

    @Override
    public String getActionName(String scopeName) {
      return VcsBundle.message("action.name.update.scope", scopeName);
    }

    @Override
    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getUpdateName();
    }

    @Override
    public boolean canGroupByChangelist() {
      return true;
    }

    @Override
    public boolean canChangeFileStatus() {
      return false;
    }
  };

  ActionInfo STATUS = new ActionInfo() {
    @Override
    public boolean showOptions(Project project) {
      return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.STATUS).getValue();
    }

    @Override
    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getStatusEnvironment();
    }

    @Override
    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project,
                                                           LinkedHashMap<Configurable, AbstractVcs> envToConfMap, final String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        @Override
        protected String getRealTitle() {
          return VcsBundle.message("action.display.name.check.scope.status", scopeName);
        }

        @Override
        protected String getActionNameForDimensions() {
          return "status";
        }

        @Override
        protected boolean isToBeShown() {
          return ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.STATUS).getValue();
        }

        @Override
        protected void setToBeShown(boolean value, boolean onOk) {
          if (onOk) {
            ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.STATUS).setValue(value);
          }
        }
      };
    }

    @Override
    public String getActionName() {
      return VcsBundle.message("action.name.check.status");
    }

    @Override
    public String getActionName(String scopeName) {
      return VcsBundle.message("action.name.check.scope.status", scopeName);
    }

    @Override
    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getStatusName();
    }

    @Override
    public boolean canGroupByChangelist() {
      return false;
    }

    @Override
    public boolean canChangeFileStatus() {
      return true;
    }
  };

  ActionInfo INTEGRATE = new ActionInfo() {
    @Override
    public boolean showOptions(Project project) {
      return true;
    }

    @Override
    public UpdateEnvironment getEnvironment(AbstractVcs vcs) {
      return vcs.getIntegrateEnvironment();
    }

    @Override
    public UpdateOrStatusOptionsDialog createOptionsDialog(final Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                           final String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, envToConfMap) {
        @Override
        protected String getRealTitle() {
          return VcsBundle.message("action.display.name.integrate.scope", scopeName);
        }

        @Override
        protected String getActionNameForDimensions() {
          return "integrate";
        }

        @Override
        protected boolean canBeHidden() {
          return false;
        }

        @Override
        protected boolean isToBeShown() {
          return true;
        }

        @Override
        protected void setToBeShown(boolean value, boolean onOk) {
        }
      };
    }

    @Override
    public boolean canChangeFileStatus() {
      return true;
    }

    @Override
    public String getActionName(String scopeName) {
      return VcsBundle.message("action.name.integrate.scope", scopeName);
    }

    @Override
    public String getActionName() {
      return VcsBundle.message("action.name.integrate");
    }

    @Override
    public String getGroupName(FileGroup fileGroup) {
      return fileGroup.getUpdateName();
    }

    @Override
    public boolean canGroupByChangelist() {
      return false;
    }
  };

  boolean showOptions(Project project);

  UpdateEnvironment getEnvironment(AbstractVcs vcs);

  UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap,
                                                  final String scopeName);

  String getActionName(String scopeName);

  String getActionName();

  String getGroupName(FileGroup fileGroup);

  boolean canGroupByChangelist();

  boolean canChangeFileStatus();
}
