// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
    public UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap, String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, VcsBundle.message("action.display.name.update.scope", scopeName), envToConfMap) {
        @Override
        @NlsSafe
        protected String getActionNameForDimensions() {
          return "update-v2";
        }

        @NotNull
        @Override
        protected String getDoNotShowMessage() {
          return VcsBundle.message("update.checkbox.don.t.show.again");
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
    public UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap, String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, VcsBundle.message("action.display.name.check.scope.status", scopeName), envToConfMap) {
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
    public UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap, String scopeName) {
      return new UpdateOrStatusOptionsDialog(project, VcsBundle.message("action.display.name.integrate.scope", scopeName), envToConfMap) {
        @Override
        @NlsSafe
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
        protected void setToBeShown(boolean value, boolean onOk) { }
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

  UpdateOrStatusOptionsDialog createOptionsDialog(Project project, LinkedHashMap<Configurable, AbstractVcs> envToConfMap, String scopeName);

  @NlsActions.ActionText String getActionName(String scopeName);

  String getActionName();

  @Nls
  String getGroupName(FileGroup fileGroup);

  boolean canGroupByChangelist();

  boolean canChangeFileStatus();
}