/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config;

import com.intellij.conversion.*;
import org.jdom.Element;

import java.util.ArrayList;

/**
 * @author nik
 */
public class GroovyProjectConverter extends ProjectConverter {
  @Override
  public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
    return new GroovyModuleConverter();
  }

  @Override
  public ConversionProcessor<WorkspaceSettings> createWorkspaceFileConverter() {
    return new ConversionProcessor<WorkspaceSettings>() {
      @Override
      public boolean isConversionNeeded(WorkspaceSettings workspaceSettings) {
        for (Element element : workspaceSettings.getRunConfigurations()) {
          final String confType = element.getAttributeValue("type");
          if ("GrailsTestsRunConfigurationType".equals(confType) || "GantScriptRunConfiguration".equals(confType)) {
            return true;
          }
        }

        return false;
      }

      @Override
      public void process(WorkspaceSettings workspaceSettings) throws CannotConvertException {
        for (Element element : new ArrayList<Element>(workspaceSettings.getRunConfigurations())) {
          final String confType = element.getAttributeValue("type");
          final boolean wasGrails = "GrailsTestsRunConfigurationType".equals(confType);
          if (wasGrails || "GantScriptRunConfiguration".equals(confType)) {
            if ("true".equals(element.getAttributeValue("default"))) {
              element.detach();
            } else {
              element.setAttribute("type", wasGrails ? "GrailsRunConfigurationType" : "GroovyScriptRunConfiguration");
              element.setAttribute("factoryName", wasGrails ? "Grails Application" : "Groovy Script");
            }
          }
        }

      }
    };
  }
}
