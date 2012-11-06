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
package org.jetbrains.jps.ant.model.impl;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.ant.model.impl.artifacts.AntArtifactExtensionProperties;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactExtensionSerializer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class JpsAntModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new JpsGlobalAntConfigurationSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new JpsProjectAntConfigurationSerializer());
  }

  @Override
  public List<? extends JpsArtifactExtensionSerializer<?>> getArtifactExtensionSerializers() {
    return Arrays.asList(new JpsAntArtifactExtensionSerializer("ant-postprocessing", JpsAntArtifactExtensionImpl.POSTPROCESSING_ROLE),
                         new JpsAntArtifactExtensionSerializer("ant-preprocessing", JpsAntArtifactExtensionImpl.PREPROCESSING_ROLE));
  }

  private static class JpsAntArtifactExtensionSerializer extends JpsArtifactExtensionSerializer<JpsAntArtifactExtension> {
    private JpsAntArtifactExtensionSerializer(final String id, final JpsElementChildRole<JpsAntArtifactExtension> role) {
      super(id, role);
    }

    @Override
    public JpsAntArtifactExtension loadExtension(@Nullable Element optionsTag) {
      AntArtifactExtensionProperties properties = optionsTag != null ? XmlSerializer.deserialize(optionsTag, AntArtifactExtensionProperties.class) : null;
      return new JpsAntArtifactExtensionImpl(properties != null ? properties : null);
    }

    @Override
    public void saveExtension(@NotNull JpsAntArtifactExtension extension, @NotNull Element optionsTag) {
      AntArtifactExtensionProperties properties = ((JpsAntArtifactExtensionImpl)extension).getProperties();
      XmlSerializer.serializeInto(properties, optionsTag, new SkipDefaultValuesSerializationFilters());
    }
  }

  private static class JpsGlobalAntConfigurationSerializer extends JpsGlobalExtensionSerializer {
    protected JpsGlobalAntConfigurationSerializer() {
      super("other.xml", "GlobalAntConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
    }
  }

  private static class JpsProjectAntConfigurationSerializer extends JpsProjectExtensionSerializer {
    private JpsProjectAntConfigurationSerializer() {
      super("ant.xml", "AntConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      Map<String, JpsAntBuildFileOptions> optionsMap = new HashMap<String, JpsAntBuildFileOptions>();
      for (Element buildFileTag : JDOMUtil.getChildren(componentTag, "buildFile")) {
        String url = buildFileTag.getAttributeValue("url");
        JpsAntBuildFileOptionsImpl options = new JpsAntBuildFileOptionsImpl();
        options.setMaxHeapSize(StringUtil.parseInt(getValueAttribute(buildFileTag, "maximumHeapSize"), 128));
        options.setMaxStackSize(StringUtil.parseInt(getValueAttribute(buildFileTag, "maximumStackSize"), 2));
        options.setCustomJdkName(getValueAttribute(buildFileTag, "customJdkName"));
        optionsMap.put(url, options);
      }
      project.getContainer().setChild(JpsAntConfigurationImpl.ROLE, new JpsAntConfigurationImpl(optionsMap));
    }

    private static String getValueAttribute(Element buildFileTag, final String childName) {
      Element child = buildFileTag.getChild(childName);
      return child != null ? child.getAttributeValue("value") : "";
    }

    @Override
    public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    }
  }
}
