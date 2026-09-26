// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionDescriptorAction
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementGroup
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xml.dom.XmlElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@TestApplication
@Suppress("UnstableApiUsage")
internal class CoreActionsPreludeTest {
  @Test
  fun preludeElementsPrecedeEveryDescriptorElementInTheRegistrationIndex() {
    val fixture = PreludeRegistrationFixture()
    val coreModule = coreModule(actionDescriptor(actionElement("prelude.order.core.main")))
    fixture.register(
      prelude = CoreActionsPrelude(coreModule = coreModule, elements = listOf(actionDescriptor(actionElement("prelude.order.first")))),
      coreModule,
      module("prelude.order.other", actionDescriptor(actionElement("prelude.order.late"))),
    )

    val preludeIndex = fixture.state.getRegistrationIndex("prelude.order.first")
    val coreMainIndex = fixture.state.getRegistrationIndex("prelude.order.core.main")
    val lateIndex = fixture.state.getRegistrationIndex("prelude.order.late")
    assertTrue(preludeIndex in 0..<coreMainIndex, "prelude=$preludeIndex, coreMain=$coreMainIndex")
    assertTrue(coreMainIndex < lateIndex, "coreMain=$coreMainIndex, late=$lateIndex")
  }

  @Test
  fun preludeReferenceToLaterDescriptorActionResolvesInTheSamePass() {
    val fixture = PreludeRegistrationFixture()
    val coreModule = coreModule()
    fixture.register(
      prelude = CoreActionsPrelude(
        coreModule = coreModule,
        elements = listOf(groupDescriptor(groupElement(
          "prelude.ref.group",
          XmlElement(name = REFERENCE_ELEMENT_NAME, attributes = mapOf(REF_ATTR_NAME to "prelude.ref.target"))))),
      ),
      coreModule,
      module("prelude.ref.provider", actionDescriptor(actionElement("prelude.ref.target"))),
    )

    val group = fixture.group("prelude.ref.group")
    assertEquals(listOf("prelude.ref.target"), childIds(group))
    assertSame(fixture.action("prelude.ref.target"), group.childActionsOrStubs.single())
  }

  @Test
  fun descriptorAddToGroupLandsInPreludeGroup() {
    val fixture = PreludeRegistrationFixture()
    val coreModule = coreModule()
    fixture.register(
      prelude = CoreActionsPrelude(
        coreModule = coreModule,
        elements = listOf(groupDescriptor(groupElement("prelude.add.group"))),
      ),
      coreModule,
      module("prelude.add.contributor",
             actionDescriptor(actionElement(
               "prelude.add.action",
               XmlElement(name = ADD_TO_GROUP_ELEMENT_NAME, attributes = mapOf(GROUP_ID_ATTR_NAME to "prelude.add.group"))))),
    )

    val group = fixture.group("prelude.add.group")
    assertSame(fixture.action("prelude.add.action"), group.childActionsOrStubs.single())
    assertEquals(listOf("prelude.add.group"), fixture.state.getParentGroupIds("prelude.add.action"))
  }

  @Test
  fun preludeKeyboardShortcutsReachKeymapOperations() {
    val fixture = PreludeRegistrationFixture()
    val coreModule = coreModule()
    val shortcutElement = XmlElement(name = "keyboard-shortcut",
                                     attributes = mapOf("first-keystroke" to "control G", "keymap" to "prelude.test.keymap"))
    fixture.register(
      prelude = CoreActionsPrelude(
        coreModule = coreModule,
        elements = listOf(actionDescriptor(actionElement("prelude.keymap.action", shortcutElement))),
      ),
      coreModule,
    )

    val operations = fixture.keymapToOperations.getValue("prelude.test.keymap")
    assertTrue(operations.any { it is AddShortcutOperation && it.actionId == "prelude.keymap.action" },
               "expected an AddShortcutOperation for prelude.keymap.action, got: $operations")
  }

  @Test
  fun loadCoreActionElementsPreservesTheFileOrder() {
    val xmlByPath = CORE_ACTION_SET_PATHS.withIndex().associate { (index, path) ->
      path to """<idea-plugin><actions><action id="prelude.load.$index" class="${PreludeTestAction::class.java.name}"/></actions></idea-plugin>"""
    }

    val elements = loadCoreActionElements { path -> xmlByPath.getValue(path).byteInputStream() }
    assertEquals(listOf("prelude.load.0", "prelude.load.1", "prelude.load.2", "prelude.load.3"),
                 elements.map { it.element.attributes[ID_ATTR_NAME] })
  }

  @Test
  fun loadCoreActionElementsFailsHardOnAMissingResource() {
    val missingPath = "idea/ExecutionActions.xml"
    val xml = """<idea-plugin><actions/></idea-plugin>"""

    val error = assertThrows<IllegalStateException> {
      loadCoreActionElements { path -> if (path == missingPath) null else xml.byteInputStream() }
    }
    assertTrue(error.message!!.contains(missingPath), "expected the message to name '$missingPath', got: ${error.message}")
  }

  @Test
  fun loadCoreActionElementsFailsHardOnAPluginAliasRow() {
    val xml = """<idea-plugin><module value="prelude.purity.alias"/><actions/></idea-plugin>"""

    val error = assertThrows<IllegalStateException> {
      loadCoreActionElements { xml.byteInputStream() }
    }
    val firstPath = CORE_ACTION_SET_PATHS.first()
    assertTrue(error.message!!.contains(firstPath) && error.message!!.contains("plugin aliases"),
               "expected the message to name '$firstPath' and 'plugin aliases', got: ${error.message}")
  }

  @Test
  fun loadCoreActionElementsFailsHardOnAnExtensionsBlock() {
    val xml = """<idea-plugin><extensions defaultExtensionNs="com.intellij"><postStartupActivity implementation="prelude.purity.Activity"/></extensions></idea-plugin>"""

    val error = assertThrows<IllegalStateException> {
      loadCoreActionElements { xml.byteInputStream() }
    }
    assertTrue(error.message!!.contains("extensions"), "expected the message to name 'extensions', got: ${error.message}")
  }

  @Test
  fun preludeConstructionRequiresTheCoreDescriptor() {
    assertThrows<IllegalArgumentException> {
      CoreActionsPrelude(coreModule = module("prelude.noncore"), elements = emptyList())
    }
  }

  @Test
  fun coreActionSetPathsMatchTheIdeaResourceDirectories() {
    val communityRoot = Path.of(PlatformTestUtil.getCommunityPath())
    val onDisk = listOf("platform/platform-impl/resources/idea", "platform/lang-impl/resources/idea")
      .flatMap { dir -> communityRoot.resolve(dir).listDirectoryEntries("*.xml") }
      .map { it.name }
      .toSet()

    val declared = CORE_ACTION_SET_PATHS.map { it.substringAfterLast('/') }.toSet()
    assertEquals(declared, onDisk,
                 "The idea/ resource directories drifted from CORE_ACTION_SET_PATHS. " +
                 "Update both mirrors together: CORE_ACTION_SET_PATHS and ModuleStructureValidator.coreActionSetDescriptors.")
  }

  private class PreludeRegistrationFixture {
    val state = ActionManagerState()
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    private val idToAction = HashMap<String, AnAction>()
    private val registrar = ActionPreInitRegistrar(idToAction, HashMap(), state)

    fun register(prelude: CoreActionsPrelude?, vararg modules: IdeaPluginDescriptorImpl) {
      ActionPluginRegistrar().registerActions(descriptors = modules.asSequence(),
                                              keymapToOperations = keymapToOperations,
                                              actionRegistrar = registrar,
                                              prelude = prelude)
    }

    fun action(id: String): AnAction? = idToAction[id]

    fun group(id: String): DefaultActionGroup = idToAction.getValue(id) as DefaultActionGroup
  }

  private fun childIds(group: DefaultActionGroup): List<String> = group.childActionsOrStubs.map { (it as ActionStubBase).id }

  private fun coreModule(vararg descriptors: ActionElement): IdeaPluginDescriptorImpl {
    return module(PluginManagerCore.CORE_ID.idString, *descriptors)
  }

  private fun module(pluginId: String, vararg descriptors: ActionElement): IdeaPluginDescriptorImpl {
    val builder = PluginDescriptorBuilder.builder()
    builder.id = pluginId
    descriptors.forEach { builder.addAction(it) }
    return PluginMainDescriptor(raw = builder.build(), pluginPath = Path.of(pluginId), isBundled = true)
  }

  private fun actionDescriptor(element: XmlElement): ActionDescriptorAction {
    return ActionDescriptorAction(className = element.attributes.getValue(CLASS_ATTR_NAME),
                                  isInternal = false,
                                  element = element,
                                  resourceBundle = null)
  }

  private fun groupDescriptor(element: XmlElement): ActionElementGroup {
    return ActionElementGroup(className = null, id = element.attributes[ID_ATTR_NAME], element = element, resourceBundle = null)
  }

  private fun actionElement(id: String, vararg children: XmlElement): XmlElement {
    val attributes = mapOf(ID_ATTR_NAME to id, CLASS_ATTR_NAME to PreludeTestAction::class.java.name)
    return XmlElement(name = ACTION_ELEMENT_NAME, attributes = attributes, children = children.toList())
  }

  private fun groupElement(id: String, vararg children: XmlElement): XmlElement {
    return XmlElement(name = GROUP_ELEMENT_NAME, attributes = mapOf(ID_ATTR_NAME to id), children = children.toList())
  }

}

private class PreludeTestAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
  }
}
