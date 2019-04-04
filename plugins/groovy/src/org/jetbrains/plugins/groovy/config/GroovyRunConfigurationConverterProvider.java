/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class GroovyRunConfigurationConverterProvider extends ConverterProvider {
  public GroovyRunConfigurationConverterProvider() {
    super("groovy-script-run-configurations");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Groovy Script run configurations will be converted into a new format";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new ProjectConverter() {
      @Override
      public ConversionProcessor<RunManagerSettings> createRunConfigurationsConverter() {
        return new ConversionProcessor<RunManagerSettings>() {
          @Override
          public boolean isConversionNeeded(RunManagerSettings settings) {
            for (Element element : settings.getRunConfigurations()) {
              if ("Groovy Script".equals(element.getAttributeValue("factoryName")) && !"true".equals(element.getAttributeValue("default"))) {
                return true;
              }
            }
            return false;
          }

          @Override
          public void process(RunManagerSettings settings) throws CannotConvertException {
            for (Element element : settings.getRunConfigurations()) {
              if ("Groovy Script".equals(element.getAttributeValue("factoryName"))) {
                element.setAttribute("factoryName", "Groovy");
              }
            }
          }
        };
      }
    };
  }
}
