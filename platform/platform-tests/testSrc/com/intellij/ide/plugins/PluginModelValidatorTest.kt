// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.CleanupSnapshots
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.getErrorsAsString
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Path

private val testSnapshotDir = Path.of(PlatformTestUtil.getCommunityPath(), "platform/platform-tests/testSnapshots/plugin-validator")

private const val TEST_PLUGIN_ID = "plugin"

@TestDataPath("\$CONTENT_ROOT/testSnapshots/plugin-validator")
class PluginModelValidatorTest {
  @Rule @JvmField val inMemoryFs = InMemoryFsRule()
  @Rule @JvmField val testName = TestName()

  private val snapshot: Path
    get() = testSnapshotDir.resolve("${sanitizeFileName(testName.methodName)}.json")

  companion object {
    @ClassRule @JvmField val cleanupSnapshots = CleanupSnapshots(testSnapshotDir)
  }

  @Test
  fun `dependency on a plugin in a new format must be in a plugin with package prefix`() {
    val root = inMemoryFs.fs.getPath("/")
    val modules = produceDependencyAndDependentPlugins(root) { it.replace(" package=\"dependentPackagePrefix\"", "") }
    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThatErrorsToMatchSnapshot(validator)
  }

  @Test
  fun `dependency on a plugin is specified as a plugin`() {
    val root = inMemoryFs.fs.getPath("/")
    val modules = produceDependencyAndDependentPlugins(root) { it }
    val validator = PluginModelValidator()
    assertThat(validator.validate(modules)).isEmpty()
    assertThat(validator.graphAsString()).toMatchSnapshot(snapshot)
  }

  @Test
  fun `dependency on a plugin must be specified as a plugin`() {
    val root = inMemoryFs.fs.getPath("/")
    val modules = produceDependencyAndDependentPlugins(root) { it.replace("<plugin id=\"dependency\"/>", "<module name=\"intellij.dependent\"/>") }

    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThatErrorsToMatchSnapshot(validator)
  }

  @Test
  fun `dependency on a plugin must be resolvable`() {
    val root = inMemoryFs.fs.getPath("/")
    val modules = produceDependencyAndDependentPlugins(root) { it.replace("<plugin id=\"dependency\"/>", "<plugin id=\"incorrectId\"/>") }

    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThatErrorsToMatchSnapshot(validator)
  }

  @Test
  fun `module must not depend on a parent plugin`() {
    val root = inMemoryFs.fs.getPath("/")
    val modules = producePluginWithContentModule(root) {
      it.replace("<plugin id=\"com.intellij.modules.lang\"/>", "<plugin id=\"$TEST_PLUGIN_ID\"/>")
    }

    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThat(validator.getErrors().joinToString { it.message!! }).isEqualTo("""
      Do not add dependency on a parent plugin (
        entry=XmlElement(name=plugin, attributes={id=plugin}, children=[], content=null),
        referencingDescriptorFile=/intellij.plugin.module/intellij.plugin.module.xml
      )
    """.trimIndent())
  }

  @Test
  fun `content module in the same source module`() {
    val modules = producePluginWithContentModuleInTheSameSourceModule(inMemoryFs.fs.getPath("/")) { it }
    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThat(validator.getErrors()).isEmpty()
    assertThat(validator.graphAsString()).toMatchSnapshot(snapshot)
  }

  @Test
  fun `validate dependencies of content module in the same source module`() {
    val modules = producePluginWithContentModuleInTheSameSourceModule(inMemoryFs.fs.getPath("/")) {
      it.replace("<dependencies>", "<dependencies><module name=\"com.intellij.diagram\"/>")
    }

    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThatErrorsToMatchSnapshot(validator)
  }

  private fun producePluginWithContentModuleInTheSameSourceModule(root: Path,
                                                                  mutator: (String) -> String): List<PluginModelValidator.Module> {
    val moduleRoot = root.resolve("intellij.angularJs")
    val module = writePluginXml("intellij.angularJs", moduleRoot, """
      <!--suppress PluginXmlValidity -->
      <idea-plugin>
        <id>AngularJs</id>
          <content>
            <module name="intellij.angularJs/diagram"/>
          </content>
      </idea-plugin>
    """)

    moduleRoot.resolve("intellij.angularJs.diagram.xml").write(mutator("""
      <idea-plugin package="org.angularjs.diagram">
        <dependencies>
        </dependencies>
      </idea-plugin>
    """))
    return listOf(module)
  }

  @Test
  fun `module must not have dependencies in old format`() {
    val root = inMemoryFs.fs.getPath("/")
    val modules = producePluginWithContentModule(root) {
      it.replace("</dependencies>", "</dependencies><depends>com.intellij.modules.lang</depends>")
    }
    val validator = PluginModelValidator()
    validator.validate(modules)
    assertThat(validator.getErrors().joinToString { it.message!! }).isEqualTo("""
      Old format must be not used for a module but `depends` tag is used (
        descriptorFile=/intellij.plugin.module/intellij.plugin.module.xml,
        depends=XmlElement(name=depends, attributes={}, children=[], content=com.intellij.modules.lang)
      )
    """.trimIndent())
  }

  private fun produceDependencyAndDependentPlugins(root: Path, mutator: (String) -> String): List<PluginModelValidator.Module> {
    val modules = mutableListOf<PluginModelValidator.Module>()
    modules.add(writePluginXml("intellij.dependency", root.resolve("dependency"), """
      <!--suppress PluginXmlValidity -->
      <idea-plugin package="dependencyPackagePrefix">
        <id>dependency</id>
      </idea-plugin>
    """))

    modules.add(writePluginXml("intellij.dependent", root.resolve("dependent"), mutator("""
      <idea-plugin package="dependentPackagePrefix">
        <id>dependent</id>
        <dependencies>
          <plugin id="dependency"/>
        </dependencies>
      </idea-plugin>
    """)))
    return modules
  }

  private fun producePluginWithContentModule(root: Path, mutator: (String) -> String): List<PluginModelValidator.Module> {
    val modules = mutableListOf<PluginModelValidator.Module>()
    modules.add(writePluginXml("intellij.plugin", root.resolve("plugin"), """
      <!--suppress PluginXmlValidity -->
      <idea-plugin package="plugin">
        <id>${TEST_PLUGIN_ID}</id>
        <content>
          <module name="intellij.plugin.module"/>
        </content>
      </idea-plugin>
    """))

    modules.add(writeModuleXml("intellij.plugin.module", root.resolve("intellij.plugin.module"), mutator("""
      <idea-plugin package="plugin.module">
        <dependencies>
          <plugin id="com.intellij.modules.lang"/>
        </dependencies>
      </idea-plugin>
    """)))
    return modules
  }

  private fun assertThatErrorsToMatchSnapshot(validator: PluginModelValidator) {
    assertThat(getErrorsAsString(validator.getErrors(), includeStackTrace = false)).toMatchSnapshot(snapshot)
  }
}

private fun writePluginXml(name: String, root: Path, @Language("xml") content: String): PluginModelValidator.Module {
  root.resolve("META-INF/plugin.xml").write(content.trimIndent())
  return TestModule(name, listOf(root))
}

private fun writeModuleXml(name: String, root: Path, @Language("xml") content: String): PluginModelValidator.Module {
  root.resolve("$name.xml").write(content.trimIndent())
  return TestModule(name, listOf(root))
}

private class TestModule(override val name: String, private val sourceRoots: List<Path>) : PluginModelValidator.Module {
  override fun getSourceRoots() = sourceRoots
}