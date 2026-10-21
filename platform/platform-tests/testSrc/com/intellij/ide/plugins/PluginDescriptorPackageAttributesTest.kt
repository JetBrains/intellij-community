// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.write
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.invariantSeparatorsPathString

class PluginDescriptorPackageAttributesTest {
  @TempDir
  lateinit var root: Path

  private val project = JpsElementFactory.getInstance().createModel().project

  @Test
  fun `rejects attributes in main content slash and product descriptors`(): Unit = timeoutRunBlocking {
    val module = createModule()
    writeXml(module, "META-INF/plugin.xml", """<idea-plugin package="main"><id>plugin</id></idea-plugin>""")
    writeXml(module, "intellij.plugin.xml", """<idea-plugin package="content"/>""")
    writeXml(module, "intellij.plugin.optional.xml", """<idea-plugin package="slash"/>""")
    val productModule = createModule("intellij.product")
    writeXml(productModule, "META-INF/IdeaPlugin.xml", """<idea-plugin package="product"><id>com.intellij</id></idea-plugin>""")
    val options = PluginValidationOptions(pluginModelBuilderOptions = SourceCodeBasedPluginModelBuilderOptions(
      corePluginDescriptions = listOf(CorePluginDescription(productModule.name, "IdeaPlugin.xml")),
    ))

    val errors = validatePluginModel(project, root, options).errors
    assertThat(errors).hasSize(4)
    assertThat(errors).allSatisfy {
      assertThat(it).hasMessageStartingWith("New 'package' attribute usages are forbidden in plugin and module descriptors.")
    }
  }

  @Test
  fun `ignores attributes in legacy optional descriptors`(): Unit = timeoutRunBlocking {
    val module = createModule()
    writeXml(module, "META-INF/plugin.xml", """
      <idea-plugin>
        <id>plugin</id>
        <depends optional="true" config-file="optional.xml">dependency</depends>
      </idea-plugin>
    """.trimIndent())
    writeXml(module, "META-INF/optional.xml", """<idea-plugin package="optional"/>""")
    val options = PluginValidationOptions(pluginsToOptionalDepends = mapOf("plugin" to setOf("dependency")))

    assertThat(validatePluginModel(project, root, options).errors).isEmpty()
  }

  @Test
  fun `checks package attributes merged from included fragments`(): Unit = timeoutRunBlocking {
    val module = createModule()
    val file = writeXml(module, "META-INF/plugin.xml", """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>plugin</id>
        <xi:include href="included.xml"/>
      </idea-plugin>
    """.trimIndent())
    writeXml(module, "META-INF/included.xml", """<idea-plugin package="included"/>""")

    val errors = validatePluginModel(project, root).errors
    assertThat(errors).hasSize(1)
    assertThat(errors.single()).hasMessageContaining(relativePath(file))
    assertThat(validatePluginModel(project, root, options(module, file)).errors).isEmpty()
  }

  @Test
  fun `allows existing attributes but rejects a new descriptor in the same module`(): Unit = timeoutRunBlocking {
    val module = createModule()
    val existingFile = writeXml(module, "intellij.plugin.xml", """<idea-plugin package="existing"/>""")
    writeXml(module, "intellij.plugin.new.xml", """<idea-plugin package="new"/>""")

    val result = validatePluginModel(project, root, options(module, existingFile))
    assertThat(result.errors).hasSize(1)
    assertThat(result.errors.single()).hasMessageContaining("intellij.plugin/intellij.plugin.new.xml")
  }

  @Test
  fun `a suppression applies only to its JPS module`(): Unit = timeoutRunBlocking {
    val module = createModule()
    val file = writeXml(module, "intellij.plugin.xml", """<idea-plugin package="existing"/>""")
    val options = PluginValidationOptions(existingPackageAttributes = mapOf("another.module" to setOf(relativePath(file))))

    assertThat(validatePluginModel(project, root, options).errors).hasSize(1)
  }

  @Test
  fun `accepts descriptors without package attributes`(): Unit = timeoutRunBlocking {
    val module = createModule()
    writeXml(module, "META-INF/plugin.xml", "<idea-plugin><id>plugin</id></idea-plugin>")
    writeXml(module, "META-INF/included.xml", "<idea-plugin/>")
    writeXml(module, "other.xml", """<other package="unrelated"><idea-plugin package="nested"/></other>""")
    writeXml(module, "comment.xml", """<!-- <idea-plugin package="comment"/> --><idea-plugin/>""")

    assertThat(validatePluginModel(project, root).errors).isEmpty()
  }

  @Test
  fun `treats an empty package attribute as absent`(): Unit = timeoutRunBlocking {
    val module = createModule()
    writeXml(module, "intellij.plugin.xml", """<idea-plugin package=""/>""")

    assertThat(validatePluginModel(project, root).errors).isEmpty()
  }

  @Test
  fun `allows a stale suppression when the attribute is removed`(): Unit = timeoutRunBlocking {
    val module = createModule()
    val file = writeXml(module, "intellij.plugin.xml", """<idea-plugin package="existing"/>""")
    val options = options(module, file)
    assertThat(validatePluginModel(project, root, options).errors).isEmpty()

    writeXml(module, "intellij.plugin.xml", "<idea-plugin/>")

    assertThat(validatePluginModel(project, root, options).errors).isEmpty()
    assertThat(validatePluginModel(project, root).errors).isEmpty()
  }

  @Test
  fun `allows a stale suppression when the descriptor is deleted`(): Unit = timeoutRunBlocking {
    val module = createModule()
    val file = writeXml(module, "intellij.plugin.xml", """<idea-plugin package="existing"/>""")
    val options = options(module, file)
    file.deleteExisting()

    assertThat(validatePluginModel(project, root, options).errors).isEmpty()
  }

  @Test
  fun `ignores XML files without a standalone descriptor`(): Unit = timeoutRunBlocking {
    val module = createModule()
    writeXml(module, "META-INF/included.xml", """<idea-plugin package="included"/>""")
    writeXml(module, "fragments/included.xml", """<idea-plugin package="nested"/>""")
    writeXml(module, "META-INF/nested/included.xml", """<idea-plugin package="nested"/>""")

    assertThat(validatePluginModel(project, root).errors).isEmpty()
  }

  @Test
  fun `ignores plugin and content descriptors in test roots`(): Unit = timeoutRunBlocking {
    val module = createModule()
    val testRoot = (root / "testResources").createDirectories()
    module.addSourceRoot(JpsPathUtil.pathToUrl(testRoot.toString()), JavaResourceRootType.TEST_RESOURCE)
    (testRoot / "META-INF/plugin.xml").write("""<idea-plugin package="test"><id>test.plugin</id></idea-plugin>""")
    (testRoot / "${module.name}.xml").write("""<idea-plugin package="test.content"/>""")
    (testRoot / "${module.name}.optional.xml").write("""<idea-plugin package="test.optional"/>""")

    assertThat(validatePluginModel(project, root).errors).isEmpty()
  }

  private fun createModule(name: String = "intellij.plugin"): JpsModule {
    val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
    val sourceRoot = (root / module.name).createDirectories()
    module.addSourceRoot(JpsPathUtil.pathToUrl(sourceRoot.toString()), JavaResourceRootType.RESOURCE)
    return module
  }

  private fun writeXml(module: JpsModule, path: String, content: String): Path {
    return (root / module.name / path).write(content)
  }

  private fun options(module: JpsModule, file: Path): PluginValidationOptions {
    return PluginValidationOptions(existingPackageAttributes = mapOf(module.name to setOf(relativePath(file))))
  }

  private fun relativePath(file: Path): String = root.relativize(file).invariantSeparatorsPathString
}
