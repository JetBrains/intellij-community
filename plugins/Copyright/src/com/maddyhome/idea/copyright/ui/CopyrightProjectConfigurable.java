package com.maddyhome.idea.copyright.ui;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

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