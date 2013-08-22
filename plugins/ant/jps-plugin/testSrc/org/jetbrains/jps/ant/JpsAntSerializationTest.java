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
package org.jetbrains.jps.ant;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.SystemProperties;
import gnu.trove.THashSet;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.JpsAntExtensionService;
import org.jetbrains.jps.ant.model.JpsAntInstallation;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class JpsAntSerializationTest extends JpsSerializationTestCase {
  public static final String PROJECT_PATH = "plugins/ant/jps-plugin/testData/ant-project";
  public static final String OPTIONS_PATH = "plugins/ant/jps-plugin/testData/config/options";

  public void testLoadArtifactProperties() {
    loadProject(PROJECT_PATH);
    List<JpsArtifact> artifacts = JpsArtifactService.getInstance().getSortedArtifacts(myProject);
    assertEquals(2, artifacts.size());
    JpsArtifact dir = artifacts.get(0);
    assertEquals("dir", dir.getName());

    JpsAntArtifactExtension preprocessing = JpsAntExtensionService.getPreprocessingExtension(dir);
    assertNotNull(preprocessing);
    assertTrue(preprocessing.isEnabled());
    assertEquals(getUrl("build.xml"), preprocessing.getFileUrl());
    assertEquals("show-message", preprocessing.getTargetName());
    assertEquals(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY,
                 assertOneElement(preprocessing.getAntProperties()).getPropertyName());

    JpsAntArtifactExtension postprocessing = JpsAntExtensionService.getPostprocessingExtension(dir);
    assertNotNull(postprocessing);
    assertEquals(getUrl("build.xml"), postprocessing.getFileUrl());
    assertEquals("create-file", postprocessing.getTargetName());
    List<BuildFileProperty> properties = postprocessing.getAntProperties();
    assertEquals(2, properties.size());
    assertEquals(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY, properties.get(0).getPropertyName());
    assertEquals(dir.getOutputPath(), properties.get(0).getPropertyValue());
    assertEquals("message.text", properties.get(1).getPropertyName());
    assertEquals("post", properties.get(1).getPropertyValue());


    JpsArtifact jar = artifacts.get(1);
    assertEquals("jar", jar.getName());
    assertNull(JpsAntExtensionService.getPostprocessingExtension(jar));
    assertNull(JpsAntExtensionService.getPreprocessingExtension(jar));
  }

  public void testLoadAntInstallations() {
    loadGlobalSettings(OPTIONS_PATH);
    JpsAntInstallation installation = JpsAntExtensionService.findAntInstallation(myModel, "Apache Ant version 1.8.2");
    assertNotNull(installation);
    assertEquals(FileUtil.toSystemIndependentName(installation.getAntHome().getAbsolutePath()),
                 FileUtil.toSystemIndependentName(new File(SystemProperties.getUserHome(), "applications/apache-ant-1.8.2").getAbsolutePath()));

    JpsAntInstallation installation2 = JpsAntExtensionService.findAntInstallation(myModel, "Patched Ant");
    assertNotNull(installation2);
    assertContainsElements(toFiles(installation2.getClasspath()),
                           PathManagerEx.findFileUnderCommunityHome("lib/ant/lib/ant.jar"),
                           PathManagerEx.findFileUnderCommunityHome("lib/asm.jar"),
                           PathManagerEx.findFileUnderCommunityHome("lib/dev/easymock.jar"));
  }

  @Override
  protected Map<String, String> getPathVariables() {
    Map<String, String> pathVariables = super.getPathVariables();
    pathVariables.put(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PlatformTestUtil.getCommunityPath());
    return pathVariables;
  }

  public void testLoadAntConfiguration() {
    loadProject(PROJECT_PATH);
    loadGlobalSettings(OPTIONS_PATH);
    String buildXmlUrl = getUrl("build.xml");
    JpsAntBuildFileOptions options = JpsAntExtensionService.getOptions(myProject, buildXmlUrl);
    assertEquals(128, options.getMaxHeapSize());
    assertEquals("-J-Dmy.ant.prop=123", options.getAntCommandLineParameters());
    assertContainsElements(toFiles(options.getAdditionalClasspath()),
                           new File(getAbsolutePath("lib/jdom.jar")),
                           new File(getAbsolutePath("ant-lib/a.jar")));
    BuildFileProperty property = assertOneElement(options.getProperties());
    assertEquals("my.property", property.getPropertyName());
    assertEquals("its value", property.getPropertyValue());

    String emptyFileUrl = getUrl("empty.xml");
    JpsAntBuildFileOptions options2 = JpsAntExtensionService.getOptions(myProject, emptyFileUrl);
    assertEquals(256, options2.getMaxHeapSize());
    assertEquals(10, options2.getMaxStackSize());
    assertEquals("1.6", options2.getCustomJdkName());

    JpsAntInstallation bundled = JpsAntExtensionService.getAntInstallationForBuildFile(myModel, buildXmlUrl);
    assertNotNull(bundled);
    assertEquals("Bundled Ant", bundled.getName());

    JpsAntInstallation installation = JpsAntExtensionService.getAntInstallationForBuildFile(myModel, emptyFileUrl);
    assertNotNull(installation);
    assertEquals("Apache Ant version 1.8.2", installation.getName());

  }

  private static Set<File> toFiles(List<String> classpath) {
    Set<File> result = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    for (String path : classpath) {
      result.add(new File(path));
    }
    return result;
  }
}
