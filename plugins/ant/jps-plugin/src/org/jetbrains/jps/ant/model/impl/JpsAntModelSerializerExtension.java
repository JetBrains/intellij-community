// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.ant.model.impl;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.JpsAntConfiguration;
import org.jetbrains.jps.ant.model.JpsAntExtensionService;
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
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

public final class JpsAntModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.singletonList(new JpsGlobalAntConfigurationSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Arrays.asList(new JpsProjectAntConfigurationSerializer(), new JpsWorkspaceAntConfigurationSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsArtifactExtensionSerializer<?>> getArtifactExtensionSerializers() {
    return Arrays.asList(new JpsAntArtifactExtensionSerializer("ant-postprocessing", JpsAntArtifactExtensionImpl.POSTPROCESSING_ROLE),
                         new JpsAntArtifactExtensionSerializer("ant-preprocessing", JpsAntArtifactExtensionImpl.PREPROCESSING_ROLE));
  }

  private static final class JpsAntArtifactExtensionSerializer extends JpsArtifactExtensionSerializer<JpsAntArtifactExtension> {
    private JpsAntArtifactExtensionSerializer(final String id, final JpsElementChildRole<JpsAntArtifactExtension> role) {
      super(id, role);
    }

    @Override
    public JpsAntArtifactExtension loadExtension(@Nullable Element optionsTag) {
      return new JpsAntArtifactExtensionImpl(
        optionsTag != null ? XmlSerializer.deserialize(optionsTag, AntArtifactExtensionProperties.class) : null);
    }
  }

  @Nullable
  private static String getValueAttribute(Element buildFileTag, final String childName) {
    Element child = buildFileTag.getChild(childName);
    return child != null ? child.getAttributeValue("value") : null;
  }

  private static class JpsGlobalAntConfigurationSerializer extends JpsGlobalExtensionSerializer {
    protected JpsGlobalAntConfigurationSerializer() {
      super("other.xml", "GlobalAntConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      for (Element antTag : JDOMUtil.getChildren(componentTag.getChild("registeredAnts"), "ant")) {
        String name = getValueAttribute(antTag, "name");
        String homeDir = getValueAttribute(antTag, "homeDir");
        List<String> classpath = new ArrayList<>();
        List<String> jarDirectories = new ArrayList<>();
        for (Element classpathItemTag : JDOMUtil.getChildren(antTag.getChild("classpath"), "classpathItem")) {
          String fileUrl = classpathItemTag.getAttributeValue("path");
          String dirUrl = classpathItemTag.getAttributeValue("dir");
          if (fileUrl != null) {
            classpath.add(JpsPathUtil.urlToPath(fileUrl));
          }
          else if (dirUrl != null) {
            jarDirectories.add(JpsPathUtil.urlToPath(dirUrl));
          }
        }

        if (name != null && homeDir != null) {
          JpsAntExtensionService.addAntInstallation(global, new JpsAntInstallationImpl(new File(homeDir), name, classpath, jarDirectories));
        }
      }
    }
  }
  private static final class JpsProjectAntConfigurationSerializer extends JpsProjectExtensionSerializer {
    private JpsProjectAntConfigurationSerializer() {
      super("ant.xml", "AntConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      Map<String, JpsAntBuildFileOptions> optionsMap = new HashMap<>();
      for (Element buildFileTag : JDOMUtil.getChildren(componentTag, "buildFile")) {
        String url = buildFileTag.getAttributeValue("url");
        JpsAntBuildFileOptionsImpl options = new JpsAntBuildFileOptionsImpl();
        options.setMaxHeapSize(StringUtil.parseInt(getValueAttribute(buildFileTag, "maximumHeapSize"), 128));
        options.setMaxStackSize(StringUtil.parseInt(getValueAttribute(buildFileTag, "maximumStackSize"), 2));
        options.setCustomJdkName(getValueAttribute(buildFileTag, "customJdkName"));
        Element antReference = buildFileTag.getChild("antReference");
        if (antReference != null) {
          options.setUseProjectDefaultAnt(Boolean.parseBoolean(antReference.getAttributeValue("projectDefault")));
          options.setAntInstallationName(antReference.getAttributeValue("name"));
        }
        for (Element classpathEntry : JDOMUtil.getChildren(buildFileTag.getChild("additionalClassPath"), "entry")) {
          String fileUrl = classpathEntry.getAttributeValue("path");
          String dirUrl = classpathEntry.getAttributeValue("dir");
          if (fileUrl != null) {
            options.addJarPath(JpsPathUtil.urlToPath(fileUrl));
          }
          else if (dirUrl != null) {
            options.addJarDirectory(JpsPathUtil.urlToPath(dirUrl));
          }
        }
        for (Element propertyTag : JDOMUtil.getChildren(buildFileTag.getChild("properties"), "property")) {
          String name = propertyTag.getAttributeValue("name");
          String value = propertyTag.getAttributeValue("value");
          if (name != null && value != null) {
            options.addProperty(name, value);
          }
        }
        optionsMap.put(url, options);
      }
      Element defaultAnt = componentTag.getChild("defaultAnt");
      String projectDefaultAntName;
      if (defaultAnt != null) {
        projectDefaultAntName = defaultAnt.getAttributeValue("name");
      }
      else {
        projectDefaultAntName = null;
      }
      project.getContainer().setChild(JpsAntConfigurationImpl.ROLE, new JpsAntConfigurationImpl(optionsMap, projectDefaultAntName));
    }
  }

  private static final class JpsWorkspaceAntConfigurationSerializer extends JpsProjectExtensionSerializer {
    private JpsWorkspaceAntConfigurationSerializer() {
      super(WORKSPACE_FILE, "antWorkspaceConfiguration");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      for (Element buildFileTag : JDOMUtil.getChildren(componentTag, "buildFile")) {
        String commandLine = getValueAttribute(buildFileTag, "antCommandLine");
        String url = buildFileTag.getAttributeValue("url");
        if (!StringUtil.isEmpty(commandLine)) {
          JpsAntConfiguration configuration = project.getContainer().getChild(JpsAntConfigurationImpl.ROLE);
          if (configuration != null) {
            configuration.getOptions(url).setAntCommandLineParameters(commandLine);
          }
        }
      }
    }
  }
}
