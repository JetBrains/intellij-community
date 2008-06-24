/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.generate.tostring.config.Config;
import org.jetbrains.generate.tostring.inspection.ClassHasNoToStringMethodInspection;
import org.jetbrains.generate.tostring.inspection.FieldNotUsedInToStringInspection;
import org.jetbrains.generate.tostring.view.ConfigUI;

import javax.swing.*;

/**
 * The IDEA component for this plugin.
 */
public class GenerateToStringPlugin implements ApplicationComponent, Configurable, JDOMExternalizable, InspectionToolProvider, ProjectManagerListener {
    private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringPlugin"); 
    private ConfigUI configUI;
    public Config config = new Config();

    @NotNull
    public String getComponentName() {
        return "GenerateToString";
    }

    public void initComponent() {
        // prepare documentation
        /*
        try {
            GenerateToStringUtils.extractDocumentation();
        } catch (IOException e) {
            throw new PluginException("Error extracting documentation from jarfile", e);
        }
        */

        // ID 11: Register our project listener so we can do resource cleanup when projects are closing
        ProjectManager.getInstance().addProjectManagerListener(this);
    }

    public void disposeComponent() {
        ProjectManager.getInstance().removeProjectManagerListener(this);
    }

    public String getDisplayName() {
        return "GenerateToString";
    }

    public Icon getIcon() {
        java.net.URL resource = getClass().getResource("/resources/configurableToStringPlugin.png");
        if (resource != null) {
            return new ImageIcon(resource);
        }
        return null;
    }

    public String getHelpTopic() {
        return null;
    }

    public JComponent createComponent() {
        return configUI = new ConfigUI(config);
    }

    public boolean isModified() {
        return ! config.equals(configUI.getConfig());
    }

    public void apply() throws ConfigurationException {
        config = configUI.getConfig();

        if (config.isEnableTemplateQuickList() && config.getSelectedQuickTemplates() == null) {
            throw new ConfigurationException("At least one template should be selected in the Template Quick Selection List");
        }

        GenerateToStringContext.setConfig(config); // update context

        if (log.isDebugEnabled()) log.debug("Config updated:\n" + config);
    }

    public void reset() {
        configUI.setConfig(config);
    }

    public void disposeUIResources() {
        configUI = null;
    }

    public Config getConfig() {
        return config;
    }

    public void readExternal(org.jdom.Element element) throws InvalidDataException {
        config.readExternal(element);

        GenerateToStringContext.setConfig(config); // update context
        if (log.isDebugEnabled()) log.debug("Config loaded at startup:\n" + config);
    }

    public void writeExternal(org.jdom.Element element) throws WriteExternalException {
        config.writeExternal(element);
    }

    public Class[] getInspectionClasses() {
        // register our inspection classes
        return new Class[] { ClassHasNoToStringMethodInspection.class,
                             FieldNotUsedInToStringInspection.class };
    }


    public void projectOpened(Project project) {
    }

    public boolean canCloseProject(Project project) {
        return true;
    }

    public void projectClosed(Project project) {
    }

    public void projectClosing(Project project) {
    }

}