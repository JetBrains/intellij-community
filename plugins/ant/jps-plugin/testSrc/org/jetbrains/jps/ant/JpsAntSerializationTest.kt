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
package org.jetbrains.jps.ant

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.SystemProperties
import com.intellij.util.io.directoryContent
import gnu.trove.THashSet
import org.jetbrains.jps.ant.model.JpsAntExtensionService
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.serialization.JpsSerializationTestCase
import java.io.File

/**
 * @author nik
 */
class JpsAntSerializationTest : JpsSerializationTestCase() {
  private lateinit var antHome: File

  override fun setUp() {
    super.setUp()
    antHome = createTempDir("antHome")
  }

  fun testLoadArtifactProperties() {
    loadProject(PROJECT_PATH)
    val artifacts = JpsArtifactService.getInstance().getSortedArtifacts(myProject)
    assertEquals(2, artifacts.size)
    val dir = artifacts[0]
    assertEquals("dir", dir.name)

    val preprocessing = JpsAntExtensionService.getPreprocessingExtension(dir)!!
    assertTrue(preprocessing.isEnabled)
    assertEquals(getUrl("build.xml"), preprocessing.fileUrl)
    assertEquals("show-message", preprocessing.targetName)
    assertEquals(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY,
                          assertOneElement(preprocessing.antProperties).getPropertyName())

    val postprocessing = JpsAntExtensionService.getPostprocessingExtension(dir)!!
    assertEquals(getUrl("build.xml"), postprocessing.fileUrl)
    assertEquals("create-file", postprocessing.targetName)
    val properties = postprocessing.antProperties
    assertEquals(2, properties.size)
    assertEquals(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY, properties[0].propertyName)
    assertEquals(dir.outputPath, properties[0].propertyValue)
    assertEquals("message.text", properties[1].propertyName)
    assertEquals("post", properties[1].propertyValue)


    val jar = artifacts[1]
    assertEquals("jar", jar.name)
    assertNull(JpsAntExtensionService.getPostprocessingExtension(jar))
    assertNull(JpsAntExtensionService.getPreprocessingExtension(jar))
  }

  fun testLoadAntInstallations() {
    directoryContent {
      file("foo.jar")
      dir("lib") {
        file("bar.jar")
      }
    }.generate(antHome)
    loadGlobalSettings(OPTIONS_PATH)
    val installation = JpsAntExtensionService.findAntInstallation(myModel, "Apache Ant version 1.8.2")
    assertNotNull(installation)
    assertEquals(FileUtil.toSystemIndependentName(installation!!.antHome.absolutePath),
                          FileUtil.toSystemIndependentName(File(SystemProperties.getUserHome(), "applications/apache-ant-1.8.2").absolutePath))

    val installation2 = JpsAntExtensionService.findAntInstallation(myModel, "Patched Ant")
    assertNotNull(installation2)
    UsefulTestCase.assertSameElements(toFiles(installation2!!.classpath),
                                      File(antHome, "foo.jar"),
                                      File(antHome, "lib/bar.jar"))
  }

  override fun getPathVariables(): Map<String, String> {
    val pathVariables = super.getPathVariables()
    pathVariables.put("MY_ANT_HOME_DIR", antHome.absolutePath)
    return pathVariables
  }

  fun testLoadAntConfiguration() {
    loadProject(PROJECT_PATH)
    loadGlobalSettings(OPTIONS_PATH)
    val buildXmlUrl = getUrl("build.xml")
    val options = JpsAntExtensionService.getOptions(myProject, buildXmlUrl)
    assertEquals(128, options.maxHeapSize)
    assertEquals("-J-Dmy.ant.prop=123", options.antCommandLineParameters)
    assertContainsElements(toFiles(options.additionalClasspath),
                                          File(getAbsolutePath("lib/jdom.jar")),
                                          File(getAbsolutePath("ant-lib/a.jar")))
    val property = assertOneElement(options.properties)
    assertEquals("my.property", property.getPropertyName())
    assertEquals("its value", property.getPropertyValue())

    val emptyFileUrl = getUrl("empty.xml")
    val options2 = JpsAntExtensionService.getOptions(myProject, emptyFileUrl)
    assertEquals(256, options2.maxHeapSize)
    assertEquals(10, options2.maxStackSize)
    assertEquals("1.6", options2.customJdkName)

    val bundled = JpsAntExtensionService.getAntInstallationForBuildFile(myModel, buildXmlUrl)!!
    assertEquals("Bundled Ant", bundled.name)

    val installation = JpsAntExtensionService.getAntInstallationForBuildFile(myModel, emptyFileUrl)!!
    assertEquals("Apache Ant version 1.8.2", installation.name)

  }

  companion object {
    val PROJECT_PATH = "plugins/ant/jps-plugin/testData/ant-project"
    val OPTIONS_PATH = "plugins/ant/jps-plugin/testData/config/options"

    private fun toFiles(classpath: List<String>): Set<File> {
      val result = THashSet(FileUtil.FILE_HASHING_STRATEGY)
      for (path in classpath) {
        result.add(File(path))
      }
      return result
    }
  }
}
