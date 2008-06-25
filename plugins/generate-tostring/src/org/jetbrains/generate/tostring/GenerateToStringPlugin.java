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

/**
 * The IDEA component for this plugin.
 */
public class GenerateToStringPlugin implements ApplicationComponent, JDOMExternalizable, InspectionToolProvider, ProjectManagerListener {
    private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringPlugin"); 
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