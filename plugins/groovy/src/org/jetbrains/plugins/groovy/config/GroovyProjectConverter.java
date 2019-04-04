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

package org.jetbrains.plugins.groovy.config;

import com.intellij.conversion.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class GroovyProjectConverter extends ProjectConverter {
  @NonNls private static final String GRAILS_TESTS_RUN_CONFIGURATION_TYPE = "GrailsTestsRunConfigurationType";
  @NonNls private static final String GANT_SCRIPT_RUN_CONFIGURATION = "GantScriptRunConfiguration";
  @NonNls private static final String GRAILS_RUN_CONFIGURATION_TYPE = "GrailsRunConfigurationType";

  @Override
  public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
    return new GroovyModuleConverter();
  }

  @Override
  public ConversionProcessor<RunManagerSettings> createRunConfigurationsConverter() {
    return new ConversionProcessor<RunManagerSettings>() {
      @Override
      public boolean isConversionNeeded(RunManagerSettings settings) {
        for (Element element : settings.getRunConfigurations()) {
          final String confType = element.getAttributeValue("type");
          if (GRAILS_TESTS_RUN_CONFIGURATION_TYPE.equals(confType) || GANT_SCRIPT_RUN_CONFIGURATION.equals(confType)) {
            return true;
          }
          if (GRAILS_RUN_CONFIGURATION_TYPE.equals(confType) && "Grails Application".equals(element.getAttributeValue("factoryName"))) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void process(RunManagerSettings settings) throws CannotConvertException {
        for (Element element : settings.getRunConfigurations()) {
          final String confType = element.getAttributeValue("type");
          final boolean wasGrails = GRAILS_TESTS_RUN_CONFIGURATION_TYPE.equals(confType);
          if (wasGrails || GANT_SCRIPT_RUN_CONFIGURATION.equals(confType)) {
            if ("true".equals(element.getAttributeValue("default"))) {
              element.detach();
            }
            else {
              element.setAttribute("type", wasGrails ? GRAILS_RUN_CONFIGURATION_TYPE : "GroovyScriptRunConfiguration");
              element.setAttribute("factoryName", wasGrails ? "Grails" : "Groovy");
            }
          }
          else if (GRAILS_RUN_CONFIGURATION_TYPE.equals(confType) &&
                   "Grails Application".equals(element.getAttributeValue("factoryName"))) {
            element.setAttribute("factoryName", "Grails");
          }
        }

      }
    };
  }
}
