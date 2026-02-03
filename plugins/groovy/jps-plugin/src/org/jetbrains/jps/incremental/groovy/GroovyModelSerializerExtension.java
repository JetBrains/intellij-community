// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Arrays;
import java.util.List;

public final class GroovyModelSerializerExtension extends JpsModelSerializerExtension {
  @Override
  public @NotNull List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Arrays.asList(new JpsProjectExtensionSerializer("groovyc.xml", "GroovyCompilerProjectConfiguration") {
      @Override
      public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
        JpsGroovySettings configuration = XmlSerializer.deserialize(componentTag, JpsGroovySettings.class);
        configuration.initExcludes();
        project.getContainer().setChild(JpsGroovySettings.ROLE, configuration);
      }
    }, new GreclipseSettingsSerializer());
  }

  private static final class GreclipseSettingsSerializer extends JpsProjectExtensionSerializer {
    private GreclipseSettingsSerializer() {
      super(GreclipseSettings.COMPONENT_FILE, GreclipseSettings.COMPONENT_NAME);
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      GreclipseSettings settings = XmlSerializer.deserialize(componentTag, GreclipseSettings.class);
      GreclipseJpsCompilerSettings component = new GreclipseJpsCompilerSettings(settings);
      project.getContainer().setChild(GreclipseJpsCompilerSettings.ROLE, component);
    }

    @Override
    public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
      GreclipseJpsCompilerSettings component = new GreclipseJpsCompilerSettings(new GreclipseSettings());
      project.getContainer().setChild(GreclipseJpsCompilerSettings.ROLE, component);
    }
  }

}
