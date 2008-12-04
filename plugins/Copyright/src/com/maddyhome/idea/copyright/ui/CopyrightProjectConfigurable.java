/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class CopyrightProjectConfigurable extends SearchableConfigurable.Parent.Abstract implements ProjectComponent {
    private Project project;
    private ProjectSettingsPanel optionsPanel = null;

    private static final Icon icon = IconLoader.getIcon("/resources/copyright32x32.png");

    private static Logger logger = Logger.getInstance(CopyrightProjectConfigurable.class.getName());



  public CopyrightProjectConfigurable(Project project) {
        this.project = project;

    }

    public void projectOpened() {

    }

    public void projectClosed() {

    }

    @NotNull
    public String getComponentName() {
        return "copyright";
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public String getDisplayName() {
        return "Copyright";
    }

    public Icon getIcon() {
        return icon;
    }

    public String getHelpTopic() {
        return getId();
    }

    public JComponent createComponent() {
        logger.info("createComponent()");
        if (optionsPanel == null) {
            optionsPanel = new ProjectSettingsPanel(project);
        }

        return optionsPanel.getMainComponent();
    }

    public boolean isModified() {
        logger.info("isModified()");
        boolean res = false;
        if (optionsPanel != null) {
            res = optionsPanel.isModified();
        }

        logger.info("isModified() = " + res);

        return res;
    }

    public void apply() throws ConfigurationException {
        logger.info("apply()");
        if (optionsPanel != null) {
            optionsPanel.apply();
        }
    }

    public void reset() {
        logger.info("reset()");
        if (optionsPanel != null) {
            optionsPanel.reset();
        }
    }

    public void disposeUIResources() {
        optionsPanel = null;
    }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public String getId() {
    return "copyright";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  protected Configurable[] buildConfigurables() {
    return new Configurable[]{new CopyrightProfilesPanel(project), new Abstract() {
       private TemplateCommentPanel myPanel = new TemplateCommentPanel(null, null, null, project);
      public String getId() {
        return "f";
      }

      public Runnable enableSearch(String option) {
        return null;
      }

      @Nls
      public String getDisplayName() {
        return "Formatting";
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return getId();
      }

      public JComponent createComponent() {
        return myPanel.createComponent();
      }

      public boolean isModified() {
        return myPanel.isModified();
      }

      public void apply() throws ConfigurationException {
        myPanel.apply();
      }

      public void reset() {
        myPanel.reset();
      }

      public void disposeUIResources() {
        myPanel.disposeUIResources();
      }

      public boolean hasOwnContent() {
        return true;
      }

      protected Configurable[] buildConfigurables() {
        final FileType[] types = FileTypeUtil.getInstance().getSupportedTypes();
        final Configurable[] children = new Configurable[types.length];
        Arrays.sort(types, new FileTypeUtil.SortByName());
        for (int i = 0; i < types.length; i++) {
          FileType type = types[i];
          children[i] = ConfigTabFactory.createConfigTab(project, type, myPanel);
        }
        return children;
      }
    }};
  }
}