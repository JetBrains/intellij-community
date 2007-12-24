package com.maddyhome.idea.copyright;

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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.maddyhome.idea.copyright.ui.ProjectSettingsPanel;

import javax.swing.*;

public class CopyrightProjectConfigurable implements ProjectComponent, Configurable {
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
        return null;
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
}