// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.CleanupSnapshots
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Path
import kotlin.io.path.div

private val testSnapshotDir = Path.of(getCommunityPath(), "platform/platform-tests/testSnapshots/plugin-validator")

private const val TEST_PLUGIN_ID = "plugin"

@TestDataPath("\$CONTENT_ROOT/testSnapshots/plugin-validator")
class PluginModelValidatorTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Rule
  @JvmField
  val testName = TestName()

  private val snapshot: Path
    get() = testSnapshotDir / "${sanitizeFileName(testName.methodName)}.json"

  private val root: Path
    get() = inMemoryFs.fs.getPath("/")

  companion object {
    @ClassRule
    @JvmField
    val cleanupSnapshots = CleanupSnapshots(testSnapshotDir)
  }

  @Test
  fun `dependency on a plugin is specified as a plugin`() {
    val modules = produceDependencyAndDependentPlugins()
    val result = validatePluginModel(modules)
    assertThat(result.errors).isEmpty()
    assertWithMatchSnapshot(result.graphAsString())
  }

  @Test
  fun `dependency on a plugin must be specified as a plugin`() {
    val modules = produceDependencyAndDependentPlugins {
      it.replace("<plugin id=\"dependency\"/>", "<module name=\"intellij.dependent\"/>")
    }

    val errors = validatePluginModel(modules).errorsAsString()
    assertWithMatchSnapshot(errors)
  }

  @Test
  fun `dependency on a plugin must be resolvable`() {
    val modules = produceDependencyAndDependentPlugins {
      it.replace("<plugin id=\"dependency\"/>", "<plugin id=\"incorrectId\"/>")
    }

    val errors = validatePluginModel(modules).errorsAsString()
    assertWithMatchSnapshot(errors)
  }

  @Test
  fun `module must not depend on a parent plugin`() {
    val modules = producePluginWithContentModule {
      it.replace("<plugin id=\"com.intellij.modules.lang\"/>", "<plugin id=\"$TEST_PLUGIN_ID\"/>")
    }

    val errors = validatePluginModel(modules)
      .errors
      .joinToString { it.message!! }
    assertThat(errors).isEqualTo("""
      Do not add dependency on a parent plugin (
        entry=PluginDependency(pluginId=plugin),
        referencingDescriptorFile=/intellij.plugin.module/intellij.plugin.module.xml
      )
    """.trimIndent())
  }

  @Test
  fun `content module in the same source module`() {
    val modules = producePluginWithContentModuleInTheSameSourceModule()
    val result = validatePluginModel(modules)
    assertThat(result.errors).isEmpty()
    assertWithMatchSnapshot(result.graphAsString())
  }

  @Test
  fun `validate dependencies of content module in the same source module`() {
    val modules = producePluginWithContentModuleInTheSameSourceModule {
      it.replace("<dependencies>", "<dependencies><module name=\"com.intellij.diagram\"/>")
    }

    val result = validatePluginModel(modules)
    assertWithMatchSnapshot(result.errorsAsString())
  }

  private fun producePluginWithContentModuleInTheSameSourceModule(
    mutator: (String) -> String = { it },
  ): List<PluginModelValidator.Module> {
    val module = writeIdeaPluginXml(
      name = "intellij.angularJs",
      sourceRoot = root / "intellij.angularJs",
      content = """
        <!--suppress PluginXmlValidity -->
        <idea-plugin>
          <id>AngularJs</id>
            <content>
              <module name="intellij.angularJs/diagram"/>
            </content>
        </idea-plugin>
      """,
    )

    writeIdeaPluginXml(
      file = (root / "intellij.angularJs" / "intellij.angularJs.diagram.xml"),
      content = """
      <idea-plugin package="org.angularjs.diagram">
        <dependencies>
        </dependencies>
      </idea-plugin>
    """,
      mutator = mutator,
    )
    return listOf(module)
  }

  @Test
  fun `module must not have dependencies in old format`() {
    val modules = producePluginWithContentModule {
      it.replace("</dependencies>", "</dependencies><depends>com.intellij.modules.lang</depends>")
    }
    val result = validatePluginModel(modules)
    assertThat(result.errors.joinToString { it.message!! }).isEqualTo("""
      Old format must be not used for a module but `depends` tag is used (
        descriptorFile=/intellij.plugin.module/intellij.plugin.module.xml,
        depends=DependsElement(pluginId=com.intellij.modules.lang)
      )
    """.trimIndent())
  }

  private fun produceDependencyAndDependentPlugins(mutator: (String) -> String = { it }): List<PluginModelValidator.Module> {
    return listOf(
      writeIdeaPluginXml(
        name = "intellij.dependency",
        sourceRoot = root / "dependency",
        content = """
            <!--suppress PluginXmlValidity -->
            <idea-plugin package="dependencyPackagePrefix">
              <id>dependency</id>
            </idea-plugin>
          """,
      ),
      writeIdeaPluginXml(
        name = "intellij.dependent",
        sourceRoot = root / "dependent",
        content = """
            <idea-plugin package="dependentPackagePrefix">
              <id>dependent</id>
              <dependencies>
                <plugin id="dependency"/>
              </dependencies>
            </idea-plugin>
          """,
        mutator = mutator,
      ),
    )
  }

  private fun producePluginWithContentModule(mutator: (String) -> String = { it }): List<PluginModelValidator.Module> {
    return listOf(
      writeIdeaPluginXml(
        name = "intellij.plugin",
        sourceRoot = root / "plugin",
        content = """
            <!--suppress PluginXmlValidity -->
            <idea-plugin package="plugin">
              <id>${TEST_PLUGIN_ID}</id>
              <content>
                <module name="intellij.plugin.module"/>
              </content>
            </idea-plugin>
          """,
      ),
      writeIdeaPluginXml(
        name = "intellij.plugin.module",
        sourceRoot = root / "intellij.plugin.module",
        path = "intellij.plugin.module",
        content = """
                  <idea-plugin package="plugin.module">
                    <dependencies>
                      <plugin id="com.intellij.modules.lang"/>
                    </dependencies>
                  </idea-plugin>
                """,
        mutator = mutator,
      )
    )
  }

  private fun assertWithMatchSnapshot(charSequence: CharSequence) = assertThat(charSequence).toMatchSnapshot(snapshot)
}

private fun writeIdeaPluginXml(file: Path, @Language("xml") content: String, mutator: (String) -> String): Path {
  return file.write(mutator(content).trimIndent())
}

private fun writeIdeaPluginXml(
  name: String,
  sourceRoot: Path,
  path: String = "META-INF/plugin",
  @Language("xml") content: String,
  mutator: (String) -> String = { it },
): PluginModelValidator.Module {
  writeIdeaPluginXml(file = sourceRoot.resolve("$path.xml"), content = content, mutator = mutator)
  return PluginModel(name, sequenceOf(sourceRoot))
}

private data class PluginModel(
  override val name: String,
  override val sourceRoots: Sequence<Path>,
) : PluginModelValidator.Module {
  override val testSourceRoots: Sequence<Path> 
    get() = emptySequence()
}

private fun PluginValidationResult.errorsAsString(): CharSequence {
  if (errors.isEmpty()) return ""
  val sb = StringBuilder()
  sb.append("${errors.size} errors:\n")
  errors.zip(errors.indices).joinTo(sb, "\n") { (error, idx) ->
    "[${idx + 1}]: ${"-".repeat(30)}\n" +
    error.message!!.trim()
  }
  sb.append("\n${"-".repeat(35)}\n")
  return sb
}