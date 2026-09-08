// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.ContentModuleDescriptor
import com.intellij.ide.plugins.DependencyIsExcluded
import com.intellij.ide.plugins.PluginIsMarkedDisabled
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.RequiredContentModuleIsExcluded
import com.intellij.dev.pluginLoading.PluginLoadingNode.DependencyNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.DescriptorNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.ExclusionChainNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.GroupNode
import com.intellij.dev.pluginLoading.PluginLoadingStateTreeBuilder.buildPluginLoadingStateTree
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsRule
import javax.swing.tree.DefaultMutableTreeNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class PluginLoadingStateModelTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
    PluginManagerCore.isUnitTestMode = true
  }

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val pluginsDirPath get() = inMemoryFs.fs.getPath("/").resolve("wd/plugins")

  @Test
  fun `a disabled plugin excludes its dependent`() {
    plugin("dep") {}.installAt(pluginsDirPath)
    plugin("main") {
      dependencies { plugin("dep") }
    }.installAt(pluginsDirPath)

    val tree = buildTree(withDisabledPlugins = arrayOf("dep"))

    val dep = tree.pluginNode("dep")
    assertThat(dep.state).isEqualTo(DescriptorLoadState.EXCLUDED)
    assertThat(dep.reason).isInstanceOf(PluginIsMarkedDisabled::class.java)

    val main = tree.pluginNode("main")
    assertThat(main.state).isEqualTo(DescriptorLoadState.EXCLUDED)
    assertThat(main.reason).isInstanceOf(DependencyIsExcluded::class.java)

    val dependency = tree.dependencyNodes(main).single()
    assertThat(dependency.kind).isEqualTo(DependencyKind.PLUGIN)
    assertThat(dependency.id).isEqualTo("dep")
    assertThat(dependency.targetState).isEqualTo(DescriptorLoadState.EXCLUDED)
  }

  @Test
  fun `the exclusion path ends at the first descriptor that failed`() {
    plugin("dep") {}.installAt(pluginsDirPath)
    plugin("mid") {
      dependencies { plugin("dep") }
    }.installAt(pluginsDirPath)
    plugin("main") {
      dependencies { plugin("mid") }
    }.installAt(pluginsDirPath)

    val tree = buildTree(withDisabledPlugins = arrayOf("dep"))

    val main = tree.pluginNode("main")
    val chain = tree.view.exclusionChain(main.descriptor)
    assertThat(chain.map { it.descriptor.pluginId.idString }).containsExactly("main", "mid", "dep")
    assertThat(chain.map { it.isRootCause }).containsExactly(false, false, true)
    assertThat(chain.last().reason).isInstanceOf(PluginIsMarkedDisabled::class.java)

    // the same path hangs under the node, so the tree alone answers why the plugin did not load
    assertThat(tree.chainNodes(main).map { it.descriptor.pluginId.idString }).containsExactly("main", "mid", "dep")

    // a dependency node carries the path of its target
    val midDependency = tree.dependencyNodes(main).single()
    assertThat(midDependency.id).isEqualTo("mid")
    assertThat(tree.chainNodes(midDependency).map { it.descriptor.pluginId.idString }).containsExactly("mid", "dep")

    // a one hop path gets no group, because the node prints that hop inline
    val mid = tree.pluginNode("mid")
    val depDependency = tree.dependencyNodes(mid).single()
    assertThat(depDependency.id).isEqualTo("dep")
    assertThat(tree.chainNodes(depDependency)).isEmpty()
    assertThat(depDependency.targetReason).isInstanceOf(PluginIsMarkedDisabled::class.java)
  }

  @Test
  fun `a loaded plugin carries no exclusion path`() {
    plugin("main") {}.installAt(pluginsDirPath)

    val tree = buildTree()

    val main = tree.pluginNode("main")
    assertThat(main.state).isEqualTo(DescriptorLoadState.LOADED)
    assertThat(tree.view.exclusionChain(main.descriptor)).isEmpty()
    assertThat(tree.chainNodes(main)).isEmpty()
  }

  @Test
  fun `an excluded required content module excludes its plugin`() {
    plugin("main") {
      content {
        module("main.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "main.required"
          dependencies { plugin("absent") }
        }
      }
    }.installAt(pluginsDirPath)

    val tree = buildTree()

    val main = tree.pluginNode("main")
    assertThat(main.state).isEqualTo(DescriptorLoadState.EXCLUDED)
    assertThat(main.reason).isInstanceOf(RequiredContentModuleIsExcluded::class.java)
    assertThat(tree.moduleNode("main.required").state).isEqualTo(DescriptorLoadState.EXCLUDED)
  }

  @Test
  fun `an excluded optional content module keeps its plugin loaded`() {
    plugin("main") {
      content {
        module("main.optional") {
          packagePrefix = "main.optional"
          dependencies { plugin("absent") }
        }
      }
    }.installAt(pluginsDirPath)

    val tree = buildTree()

    assertThat(tree.pluginNode("main").state).isEqualTo(DescriptorLoadState.LOADED)
    assertThat(tree.moduleNode("main.optional").state).isEqualTo(DescriptorLoadState.EXCLUDED)
  }

  @Test
  fun `a dependency on an absent id has no target`() {
    plugin("main") {
      dependencies { plugin("absent") }
    }.installAt(pluginsDirPath)

    val tree = buildTree()

    val dependency = tree.dependencyNodes(tree.pluginNode("main")).single()
    assertThat(dependency.id).isEqualTo("absent")
    assertThat(dependency.target).isNull()
    assertThat(dependency.targetState).isNull()
  }

  @Test
  fun `problemsOnly drops a plugin that loaded`() {
    plugin("good") {}.installAt(pluginsDirPath)
    plugin("bad") {}.installAt(pluginsDirPath)

    val pluginSet = pluginSet(withDisabledPlugins = arrayOf("bad"))

    val full = buildPluginLoadingStateTree(pluginSet, problemsOnly = false)
    assertThat(full.pluginIds()).containsExactlyInAnyOrder("good", "bad")
    assertThat(full.pluginCount).isEqualTo(2)
    assertThat(full.loadedPluginCount).isEqualTo(1)
    assertThat(full.problemPluginCount).isEqualTo(1)

    val problems = buildPluginLoadingStateTree(pluginSet, problemsOnly = true)
    assertThat(problems.pluginIds()).containsExactly("bad")
    assertThat(problems.pluginCount).isEqualTo(2)
  }

  private fun pluginSet(withDisabledPlugins: Array<String> = emptyArray()): PluginSet =
    PluginSetTestBuilder.fromPath(pluginsDirPath)
      .withDisabledPlugins(*withDisabledPlugins)
      .build()

  private fun buildTree(withDisabledPlugins: Array<String> = emptyArray()): PluginLoadingStateTree =
    buildPluginLoadingStateTree(pluginSet(withDisabledPlugins), problemsOnly = false)

  private fun PluginLoadingStateTree.descriptorNodes(): Sequence<DescriptorNode> =
    root.depthFirstEnumeration().asSequence()
      .filterIsInstance<DefaultMutableTreeNode>()
      .mapNotNull { it.userObject as? DescriptorNode }

  private fun PluginLoadingStateTree.pluginIds(): List<String> =
    root.children().asSequence()
      .filterIsInstance<DefaultMutableTreeNode>()
      .mapNotNull { it.userObject as? DescriptorNode }
      .map { it.descriptor.pluginId.idString }
      .toList()

  private fun PluginLoadingStateTree.pluginNode(id: String): DescriptorNode =
    descriptorNodes().single { it.descriptor is PluginMainDescriptor && it.descriptor.pluginId.idString == id }

  private fun PluginLoadingStateTree.moduleNode(name: String): DescriptorNode =
    descriptorNodes().single { (it.descriptor as? ContentModuleDescriptor)?.moduleId?.name == name }

  private fun PluginLoadingStateTree.dependencyNodes(owner: DescriptorNode): List<DependencyNode> =
    nodeByDescriptor.getValue(owner.descriptor).groupChildren<DependencyNode>(NodeGroup.DEPENDENCIES).toList()

  private fun PluginLoadingStateTree.chainNodes(owner: DescriptorNode): List<ExclusionChainNode> =
    nodeByDescriptor.getValue(owner.descriptor).groupChildren<ExclusionChainNode>(NodeGroup.EXCLUSION_CHAIN).toList()

  /** A dependency node is not in `nodeByDescriptor`, so find it by the identity of its user object. */
  private fun PluginLoadingStateTree.chainNodes(owner: DependencyNode): List<ExclusionChainNode> =
    root.depthFirstEnumeration().asSequence()
      .filterIsInstance<DefaultMutableTreeNode>()
      .single { it.userObject === owner }
      .groupChildren<ExclusionChainNode>(NodeGroup.EXCLUSION_CHAIN)
      .toList()

  private inline fun <reified T> DefaultMutableTreeNode.groupChildren(group: NodeGroup): Sequence<T> =
    children().asSequence()
      .filterIsInstance<DefaultMutableTreeNode>()
      .filter { (it.userObject as? GroupNode)?.group == group }
      .flatMap { it.children().asSequence() }
      .filterIsInstance<DefaultMutableTreeNode>()
      .mapNotNull { it.userObject as? T }
}
