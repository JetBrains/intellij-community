// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginUtils.getClassFromKnownMessages
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.util.lang.UrlClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

private const val BUNDLED_PLUGIN = "com.example.bundled"
private const val THIRD_PARTY_PLUGIN = "com.example.thirdParty"
private const val BUNDLED_CLASS = "com.example.bundled.BundledAction"
private const val THIRD_PARTY_CLASS = "com.example.thirdParty.ThirdPartyAction"

internal class PluginUtilsTest {

  @Test
  fun `plugin exception is attributed without a plugin set`() {
    val pluginId = PluginId.getId("com.example.plugin")

    val result = PluginUtils.findPlugin(PluginException("failure", pluginId), pluginSet = null)

    assertThat(result?.first).isEqualTo(pluginId)
    assertThat(result?.second).isNull()
  }

  @Test
  fun `wrapped plugin exception is attributed without a plugin set`() {
    val pluginId = PluginId.getId("com.example.plugin")
    val error = RuntimeException("outer", PluginException("failure", pluginId))

    val result = PluginUtils.findPlugin(error, pluginSet = null)

    assertThat(result?.first).isEqualTo(pluginId)
    assertThat(result?.second).isNull()
  }

  @Test
  fun `plugin exception descriptor is resolved from the plugin set`() {
    val pluginSet = testPluginSet()
    val error = PluginException("failure", PluginId.getId(THIRD_PARTY_PLUGIN))

    val result = PluginUtils.findPlugin(error, pluginSet)

    assertThat(result?.first).isEqualTo(PluginId.getId(THIRD_PARTY_PLUGIN))
    assertThat(result?.second).isSameAs(pluginSet.getEnabledPlugin(THIRD_PARTY_PLUGIN))
  }

  @Test
  fun `third-party plugin takes precedence over bundled plugin`() {
    val pluginSet = testPluginSet()
    val error = RuntimeException().apply {
      stackTrace = arrayOf(stackFrame(BUNDLED_CLASS), stackFrame(THIRD_PARTY_CLASS))
    }

    val result = PluginUtils.findPlugin(error, pluginSet)

    assertThat(result?.first).isEqualTo(PluginId.getId(THIRD_PARTY_PLUGIN))
    assertThat(result?.second).isSameAs(pluginSet.getEnabledPlugin(THIRD_PARTY_PLUGIN))
  }

  @Test
  fun `bundled plugin is retained when cause is not attributed`() {
    val pluginSet = testPluginSet()
    val cause = RuntimeException("cause").apply { stackTrace = emptyArray() }
    val error = RuntimeException("outer", cause).apply {
      stackTrace = arrayOf(stackFrame(BUNDLED_CLASS))
    }

    val result = PluginUtils.findPlugin(error, pluginSet)

    assertThat(result?.first).isEqualTo(PluginId.getId(BUNDLED_PLUGIN))
    assertThat(result?.second).isSameAs(pluginSet.getEnabledPlugin(BUNDLED_PLUGIN))
  }

  @Test
  fun `cause attribution wins over the bundled fallback`() {
    val pluginSet = testPluginSet()
    val cause = RuntimeException("cause").apply {
      stackTrace = arrayOf(stackFrame(THIRD_PARTY_CLASS))
    }
    val error = RuntimeException("outer", cause).apply {
      stackTrace = arrayOf(stackFrame(BUNDLED_CLASS))
    }

    val result = PluginUtils.findPlugin(error, pluginSet)

    assertThat(result?.first).isEqualTo(PluginId.getId(THIRD_PARTY_PLUGIN))
  }

  @Test
  fun `class not found exception is attributed by its message`() {
    val pluginSet = testPluginSet()
    val error = ClassNotFoundException("$THIRD_PARTY_CLASS PluginClassLoader(plugin=PluginMainDescriptor(id=$THIRD_PARTY_PLUGIN), packagePrefix=null)").apply {
      stackTrace = emptyArray()
    }

    val result = PluginUtils.findPlugin(error, pluginSet)

    assertThat(result?.first).isEqualTo(PluginId.getId(THIRD_PARTY_PLUGIN))
  }

  @Test
  fun `abstract method error is attributed by its message`() {
    val pluginSet = testPluginSet()
    val error = AbstractMethodError("Method com/example/thirdParty/ThirdPartyAction.run()V is abstract").apply {
      stackTrace = emptyArray()
    }

    val result = PluginUtils.findPlugin(error, pluginSet)

    assertThat(result?.first).isEqualTo(PluginId.getId(THIRD_PARTY_PLUGIN))
  }

  @Test
  fun `class name is parsed from real class not found messages`() {
    assertThat(getClassFromKnownMessages(
      ClassNotFoundException("com.example.Missing")))
      .isEqualTo("com.example.Missing")
    assertThat(getClassFromKnownMessages(
      ClassNotFoundException("org.angular2.navigation.Angular2GotoRelatedToolbarProvider PluginClassLoader(plugin=PluginMainDescriptor(id=AngularJS), packagePrefix=null)")))
      .isEqualTo("org.angular2.navigation.Angular2GotoRelatedToolbarProvider")
  }

  @Test
  fun `class name is parsed from real no class def found messages`() {
    assertThat(getClassFromKnownMessages(
      NoClassDefFoundError("org/apache/logging/log4j/ThreadContext")))
      .isEqualTo("org.apache.logging.log4j.ThreadContext")
    assertThat(getClassFromKnownMessages(
      NoClassDefFoundError("Could not initialize class android.databinding.tool.ext.ExtKt")))
      .isEqualTo("android.databinding.tool.ext.ExtKt")
  }

  @Test
  fun `class name is parsed from real abstract method error messages`() {
    assertThat(getClassFromKnownMessages(
      AbstractMethodError("Method com/intellij/polySymbols/js/nodejs/PackageJsonSymbolScopeImpl.getModificationTracker()Lcom/intellij/openapi/util/ModificationTracker; is abstract")))
      .isEqualTo("com.intellij.polySymbols.js.nodejs.PackageJsonSymbolScopeImpl")
    assertThat(getClassFromKnownMessages(
      AbstractMethodError("Method com/haulmont/jmixstudio/intellij/js/PackageJsonNotifierConfiguration.detectPackageJsonFiles(Lkotlin/coroutines/Continuation;)Ljava/lang/Object; is abstract")))
      .isEqualTo("com.haulmont.jmixstudio.intellij.js.PackageJsonNotifierConfiguration")
    assertThat(getClassFromKnownMessages(
      AbstractMethodError("Method git4idea/checkin/GitCheckinEnvironment.isAmendSpecificCommitSupported()Z is abstract")))
      .isEqualTo("git4idea.checkin.GitCheckinEnvironment")
    assertThat(getClassFromKnownMessages(
      AbstractMethodError("Receiver class com.intellij.jupyter.lsp.JupyterNotebookDocumentAdapter does not define or inherit an implementation of the resolved method 'abstract boolean acceptsFile(com.intellij.openapi.vfs.VirtualFile, boolean)' of interface com.intellij.platform.lsp.impl.LspDocumentAdapter.")))
      .isEqualTo("com.intellij.jupyter.lsp.JupyterNotebookDocumentAdapter")
    assertThat(getClassFromKnownMessages(
      AbstractMethodError("Missing implementation of resolved method 'abstract java.lang.String getPluginId()' of abstract class org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.")))
      .isEqualTo("org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar")
    assertThat(getClassFromKnownMessages(
      AbstractMethodError("javax.ws.rs.core.UriBuilder.uri(Ljava/lang/String;)Ljavax/ws/rs/core/UriBuilder;")))
      .isEqualTo("javax.ws.rs.core.UriBuilder")
  }

  @Test
  fun `class name is parsed from real no such method messages`() {
    assertThat(getClassFromKnownMessages(NoSuchMethodException("my.package.MyClass.myMethod(boolean)")))
      .isEqualTo("my.package.MyClass")
    assertThat(getClassFromKnownMessages(NoSuchMethodException("com.idtech.BaseMod.<init>()")))
      .isEqualTo("com.idtech.BaseMod")
  }

  @Test
  fun `unrecognized messages yield no class`() {
    assertThat(getClassFromKnownMessages(AbstractMethodError("something entirely different"))).isNull()
    assertThat(getClassFromKnownMessages(NoSuchMethodException("myMethod(boolean)"))).isNull()
    assertThat(getClassFromKnownMessages(RuntimeException("com.example.Foo"))).isNull()
    assertThat(getClassFromKnownMessages(ClassNotFoundException())).isNull()
  }

  private fun testPluginSet(): PluginSet {
    val pluginSet = PluginSetTestBuilder.fromDescriptors {
      listOf(
        pluginDescriptor(BUNDLED_PLUGIN, isBundled = true),
        pluginDescriptor(THIRD_PARTY_PLUGIN, isBundled = false),
      )
    }.buildState(configureClassLoaders = false).pluginSet
    pluginSet.getEnabledPlugin(BUNDLED_PLUGIN).setPluginClassLoader(PluginClassesLoader(setOf(BUNDLED_CLASS)))
    pluginSet.getEnabledPlugin(THIRD_PARTY_PLUGIN).setPluginClassLoader(PluginClassesLoader(setOf(THIRD_PARTY_CLASS)))
    return pluginSet
  }

  private fun pluginDescriptor(id: String, isBundled: Boolean): PluginMainDescriptor {
    val raw = PluginDescriptorBuilder.builder().apply {
      this.id = id
      version = "1.0"
    }.build()
    return PluginMainDescriptor(raw = raw, pluginPath = Path.of(id), isBundled = isBundled)
  }

  private fun stackFrame(className: String): StackTraceElement =
    StackTraceElement(className, "run", "PluginUtilsTest.kt", 1)

  private class PluginClassesLoader(private val classNames: Set<String>) : UrlClassLoader(build(), false) {
    override fun hasLoadedClass(name: String): Boolean = name in classNames
  }
}
