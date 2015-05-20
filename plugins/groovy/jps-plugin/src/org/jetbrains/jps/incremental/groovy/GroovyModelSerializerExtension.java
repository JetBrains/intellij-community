/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class GroovyModelSerializerExtension extends JpsModelSerializerExtension {

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Arrays.asList(new JpsProjectExtensionSerializer("groovyc.xml", "GroovyCompilerProjectConfiguration") {
      @Override
      public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
        JpsGroovySettings configuration = XmlSerializer.deserialize(componentTag, JpsGroovySettings.class);
        if (configuration == null) {
          configuration = new JpsGroovySettings();
        }
        configuration.initExcludes();
        project.getContainer().setChild(JpsGroovySettings.ROLE, configuration);
      }

      @Override
      public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      }
    }, new GreclipseSettingsSerializer());
  }

  private static class GreclipseSettingsSerializer extends JpsProjectExtensionSerializer {
    private GreclipseSettingsSerializer() {
      super(GreclipseSettings.COMPONENT_FILE, GreclipseSettings.COMPONENT_NAME);
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      GreclipseSettings settings = XmlSerializer.deserialize(componentTag, GreclipseSettings.class);
      if (settings == null) settings = new GreclipseSettings();
      GreclipseJpsCompilerSettings component = new GreclipseJpsCompilerSettings(settings);
      project.getContainer().setChild(GreclipseJpsCompilerSettings.ROLE, component);
    }

    @Override
    public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
      GreclipseJpsCompilerSettings component = new GreclipseJpsCompilerSettings(new GreclipseSettings());
      project.getContainer().setChild(GreclipseJpsCompilerSettings.ROLE, component);
    }

    @Override
    public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) { }
  }

}
