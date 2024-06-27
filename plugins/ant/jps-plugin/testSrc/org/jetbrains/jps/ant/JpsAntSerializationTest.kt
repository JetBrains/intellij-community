// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.ant

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.util.SystemProperties
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.util.io.directoryContent
import org.jetbrains.jps.ant.model.JpsAntExtensionService
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.serialization.JpsGlobalSettingsLoading.loadGlobalSettings
import org.jetbrains.jps.model.serialization.JpsProjectData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class JpsAntSerializationTest {
  @Test
  fun testLoadArtifactProperties() {
    val projectData = JpsProjectData.loadFromTestData(PROJECT_PATH, javaClass)
    val myProject = projectData.project
    val artifacts = JpsArtifactService.getInstance().getSortedArtifacts(myProject)
    assertEquals(2, artifacts.size)
    val dir = artifacts[0]
    assertEquals("dir", dir.name)

    val preprocessing = JpsAntExtensionService.getPreprocessingExtension(dir)!!
    assertTrue(preprocessing.isEnabled)
    assertEquals(projectData.getUrl("build.xml"), preprocessing.fileUrl)
    assertEquals("show-message", preprocessing.targetName)
    assertEquals(JpsAntArtifactExtensionImpl.ARTIFACT_OUTPUT_PATH_PROPERTY,
                 assertOneElement(preprocessing.antProperties).getPropertyName())

    val postprocessing = JpsAntExtensionService.getPostprocessingExtension(dir)!!
    assertEquals(projectData.getUrl("build.xml"), postprocessing.fileUrl)
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

  @Test
  fun testLoadAntInstallations() {
    val antHome = directoryContent {
      file("foo.jar")
      dir("lib") {
        file("bar.jar")
      }
    }.generateInTempDir()
    val model = org.jetbrains.jps.model.serialization.loadGlobalSettings(OPTIONS_PATH, javaClass, mapOf(
      "MY_ANT_HOME_DIR" to antHome.absolutePathString()
    ))
    val installation = JpsAntExtensionService.findAntInstallation(model, "Apache Ant version 1.8.2")
    assertNotNull(installation)
    assertEquals(FileUtil.toSystemIndependentName(installation!!.antHome.absolutePath),
                 FileUtil.toSystemIndependentName(
                   File(SystemProperties.getUserHome(), "applications/apache-ant-1.8.2").absolutePath))

    val installation2 = JpsAntExtensionService.findAntInstallation(model, "Patched Ant")
    assertNotNull(installation2)
    UsefulTestCase.assertSameElements(toFiles(installation2!!.classpath),
                                      File(antHome.toFile(), "foo.jar"),
                                      File(antHome.toFile(), "lib/bar.jar"))
  }

  @Test
  fun testLoadAntConfiguration() {
    val projectData = JpsProjectData.loadFromTestData(PROJECT_PATH, javaClass)
    val model = projectData.project.model
    loadGlobalSettings(model.global, Path(PathManagerEx.getCommunityHomePath()).resolve(OPTIONS_PATH))
    val buildXmlUrl = projectData.getUrl("build.xml")
    val options = JpsAntExtensionService.getOptions(projectData.project, buildXmlUrl)
    assertEquals(128, options.maxHeapSize)
    assertEquals("-J-Dmy.ant.prop=123", options.antCommandLineParameters)
    assertContainsElements(toFiles(options.additionalClasspath),
                           projectData.baseProjectDir.resolve("lib/jdom.jar").toFile(),
                           projectData.baseProjectDir.resolve("ant-lib/a.jar").toFile())
    val property = assertOneElement(options.properties)
    assertEquals("my.property", property.getPropertyName())
    assertEquals("its value", property.getPropertyValue())

    val emptyFileUrl = projectData.getUrl("empty.xml")
    val options2 = JpsAntExtensionService.getOptions(projectData.project, emptyFileUrl)
    assertEquals(256, options2.maxHeapSize)
    assertEquals(10, options2.maxStackSize)
    assertEquals("1.6", options2.customJdkName)

    val bundled = JpsAntExtensionService.getAntInstallationForBuildFile(model, buildXmlUrl)!!
    assertEquals("Bundled Ant", bundled.name)

    val installation = JpsAntExtensionService.getAntInstallationForBuildFile(model, emptyFileUrl)!!
    assertEquals("Apache Ant version 1.8.2", installation.name)

  }

  companion object {
    const val PROJECT_PATH = "plugins/ant/jps-plugin/testData/ant-project"
    const val OPTIONS_PATH = "plugins/ant/jps-plugin/testData/config/options"

    private fun toFiles(classpath: List<String>): Set<File> {
      val result = FileCollectionFactory.createCanonicalFileSet()
      for (path in classpath) {
        result.add(File(path))
      }
      return result
    }
  }
}
