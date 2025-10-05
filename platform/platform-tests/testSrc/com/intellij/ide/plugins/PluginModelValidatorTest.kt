// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.CleanupSnapshots
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.invariantSeparatorsPathString

private val testSnapshotDir = Path.of(getCommunityPath(), "platform/platform-tests/testSnapshots/plugin-validator")

private const val TEST_PLUGIN_ID = "plugin"

@TestDataPath("\$CONTENT_ROOT/testSnapshots/plugin-validator")
class PluginModelValidatorTest {
  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val testName = TestName()

  private val snapshot: Path
    get() = testSnapshotDir / "${sanitizeFileName(testName.methodName)}.json"

  private val root: Path
    get() = tempDirectory.rootPath

  companion object {
    @ClassRule
    @JvmField
    val cleanupSnapshots = CleanupSnapshots(testSnapshotDir)
  }

  @Test
  fun `dependency on a plugin is specified as a plugin`() {
    val project = produceDependencyAndDependentPlugins()
    val result = validatePluginModel(project)
    assertThat(result.errors).isEmpty()
    assertWithMatchSnapshot(result.graphAsString(root))
  }

  @Test
  fun `dependency on a plugin must be specified as a plugin`() {
    val project = produceDependencyAndDependentPlugins {
      it.replace("<plugin id=\"dependency\"/>", "<module name=\"intellij.dependent\"/>")
    }

    val errors = validatePluginModel(project).errorsAsString()
    assertWithMatchSnapshot(errors)
  }

  @Test
  fun `dependency on a plugin must be resolvable`() {
    val project = produceDependencyAndDependentPlugins {
      it.replace("<plugin id=\"dependency\"/>", "<plugin id=\"incorrectId\"/>")
    }

    val errors = validatePluginModel(project).errorsAsString()
    assertWithMatchSnapshot(errors)
  }

  @Test
  fun `module must not depend on a parent plugin`() {
    val project = producePluginWithContentModule {
      it.replace("<plugin id=\"com.intellij.modules.lang\"/>", "<plugin id=\"$TEST_PLUGIN_ID\"/>")
    }

    val errors = validatePluginModel(project)
      .errors
    assertThat(errors).isEmpty()
  }

  @Test
  fun `content module in the same source module`() {
    val project = producePluginWithContentModuleInTheSameSourceModule()
    val result = validatePluginModel(project)
    assertThat(result.errors).isEmpty()
    assertWithMatchSnapshot(result.graphAsString(root))
  }

  @Test
  fun `validate dependencies of content module in the same source module`() {
    val project = producePluginWithContentModuleInTheSameSourceModule {
      it.replace("<dependencies>", "<dependencies><module name=\"com.intellij.diagram\"/>")
    }

    val result = validatePluginModel(project)
    assertWithMatchSnapshot(result.errorsAsString())
  }

  private fun validatePluginModel(project: JpsProject): PluginValidationResult = validatePluginModel(project, root)

  private fun producePluginWithContentModuleInTheSameSourceModule(
    mutator: (String) -> String = { it },
  ): JpsProject {
    val project = JpsElementFactory.getInstance().createModel().project
    createModuleWithXml(
      name = "intellij.angularJs",
      project = project,
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
    return project
  }

  @Test
  fun `module must not have dependencies in old format`() {
    val project = producePluginWithContentModule {
      it.replace("</dependencies>", "</dependencies><depends>com.intellij.modules.lang</depends>")
    }
    val result = validatePluginModel(project, root, PluginValidationOptions(
      referencedPluginIdsOfExternalPlugins = setOf("com.intellij.modules.lang")
    ))
    assertThat(result.errors.joinToString { it.message!! }).isEqualTo("""
      Element 'depends' has no effect in a content module descriptor (
        referencedDescriptorFile=intellij.plugin.module/intellij.plugin.module.xml
      ), Old format must be not used for a module but `depends` tag is used (
        descriptorFile=intellij.plugin.module/intellij.plugin.module.xml,
        depends=DependsElement(pluginId=com.intellij.modules.lang)
      )
    """.trimIndent())
  }

  @Test
  fun `dependencies from required to optional are not allowed`() {
    val project = JpsElementFactory.getInstance().createModel().project
    createModuleWithXml(
      name = "intellij.plugin",
      project = project,
      sourceRoot = root / "plugin",
      content = """
            <idea-plugin>
               <id>intellij.plugin</id>
               <content>
                  <module name="intellij.optional.module"/>
                  <module name="intellij.required.module" loading='required'/>
               </content>
            </idea-plugin>
      """)
    createContentModule(project, "intellij.optional.module", "<idea-plugin/>")
    createContentModule(project, "intellij.required.module", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.optional.module"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    val result = validatePluginModel(project)
    assertThat(result.errorsAsString()).contains("""
      The content module 'intellij.required.module' is registered as 'required', but it depends on the module 'intellij.optional.module' which is declared as optional
    """.trimIndent())
  }
  
  @Test
  fun `dependencies from embedded to optional are not allowed`() {
    val project = JpsElementFactory.getInstance().createModel().project
    createModuleWithXml(
      name = "intellij.plugin",
      project = project,
      sourceRoot = root / "plugin",
      content = """
            <idea-plugin>
               <id>intellij.plugin</id>
               <content>
                  <module name="intellij.optional.module"/>
                  <module name="intellij.embedded.module" loading='embedded'/>
               </content>
            </idea-plugin>
      """)
    createContentModule(project, "intellij.optional.module", "<idea-plugin/>")
    createContentModule(project, "intellij.embedded.module", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.optional.module"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    val result = validatePluginModel(project)
    assertThat(result.errorsAsString()).contains("""
      The content module 'intellij.embedded.module' is registered as 'embedded', but it depends on the module 'intellij.optional.module' which is declared as optional
    """.trimIndent())
  }
  
  private fun createContentModule(project: JpsProject, name: String, @Language("xml") content: String) {
    createModuleWithXml(
      name = name,
      project = project,
      sourceRoot = root / name,
      path = name,
      content = content,
    )
  }

  private fun produceDependencyAndDependentPlugins(mutator: (String) -> String = { it }): JpsProject {
    val project = JpsElementFactory.getInstance().createModel().project
    createModuleWithXml(
      name = "intellij.dependency",
      project = project,
      sourceRoot = root / "dependency",
      content = """
            <!--suppress PluginXmlValidity -->
            <idea-plugin package="dependencyPackagePrefix">
              <id>dependency</id>
            </idea-plugin>
          """,
    )
    createModuleWithXml(
      name = "intellij.dependent",
      project = project,
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
    )
    return project
  }

  private fun producePluginWithContentModule(mutator: (String) -> String = { it }): JpsProject {
    val project = JpsElementFactory.getInstance().createModel().project
    createModuleWithXml(
      name = "intellij.plugin",
      project = project,
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
    )
    createModuleWithXml(
      name = "intellij.plugin.module",
      project = project,
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
    return project
  }

  private fun assertWithMatchSnapshot(charSequence: CharSequence) = assertThat(charSequence).toMatchSnapshot(snapshot)
}

private fun writeIdeaPluginXml(file: Path, @Language("xml") content: String, mutator: (String) -> String): Path {
  return file.write(mutator(content).trimIndent())
}

private fun createModuleWithXml(
  name: String,
  project: JpsProject,
  sourceRoot: Path,
  path: String = "META-INF/plugin",
  @Language("xml") content: String,
  mutator: (String) -> String = { it },
) {
  writeIdeaPluginXml(file = sourceRoot.resolve("$path.xml"), content = content, mutator = mutator)
  val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
  module.addSourceRoot(JpsPathUtil.pathToUrl(sourceRoot.invariantSeparatorsPathString), JavaSourceRootType.SOURCE)
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