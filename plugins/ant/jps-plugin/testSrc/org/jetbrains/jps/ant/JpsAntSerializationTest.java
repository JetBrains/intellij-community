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

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.JpsAntExtensionService;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

import java.util.List;

/**
 * @author nik
 */
public class JpsAntSerializationTest extends JpsSerializationTestCase {
  public static final String PROJECT_PATH = "plugins/ant/jps-plugin/testData/ant-project";

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

  public void testLoadAntConfiguration() {
    loadProject(PROJECT_PATH);
    JpsAntBuildFileOptions options = JpsAntExtensionService.getOptions(myProject, getUrl("build.xml"));
    assertEquals(128, options.getMaxHeapSize());

    JpsAntBuildFileOptions options2 = JpsAntExtensionService.getOptions(myProject, getUrl("empty.xml"));
    assertEquals(256, options2.getMaxHeapSize());
    assertEquals(10, options2.getMaxStackSize());
    assertEquals("1.6", options2.getCustomJdkName());
  }
}
