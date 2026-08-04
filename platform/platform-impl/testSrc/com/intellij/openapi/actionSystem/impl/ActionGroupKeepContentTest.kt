// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.DefaultCompactActionGroup
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionDescriptorAction
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementGroup
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xml.dom.XmlElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
@Suppress("UnstableApiUsage")
internal class ActionGroupKeepContentTest {
  @Test
  fun overrideTransplantsChildrenInOrderAndAppendsOwnXmlChildren() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.order.a",
             groupDescriptor(groupElement("keep.order.group",
                                          children = listOf(actionElement("keep.order.first"),
                                                            actionElement("keep.order.second"))))),
    )
    val replaced = fixture.group("keep.order.group")

    fixture.register(
      module("keep.order.b",
             groupDescriptor(groupElement("keep.order.group",
                                          overrides = true,
                                          keepContent = true,
                                          children = listOf(actionElement("keep.order.own"))))),
    )

    val replacement = fixture.group("keep.order.group")
    assertNotSame(replaced, replacement)
    assertEquals(listOf("keep.order.first", "keep.order.second", "keep.order.own"), childNames(replacement))
    assertSame(replaced.childActionsOrStubs[0], replacement.childActionsOrStubs[0])
  }

  @Test
  fun ctorPopulatedChildrenPrecedeTransplantedChildrenAfterUnstub() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.ctor.a",
             groupDescriptor(groupElement("keep.ctor.group", children = listOf(actionElement("keep.ctor.transplanted"))))),
      module("keep.ctor.b",
             groupDescriptor(groupElement("keep.ctor.group",
                                          className = CtorChildrenGroup::class.java.name,
                                          overrides = true,
                                          keepContent = true))),
    )

    val group = fixture.unstub("keep.ctor.group") as DefaultActionGroup
    assertEquals(CtorChildrenGroup::class.java, group.javaClass)
    assertEquals(listOf("keep.ctor.own", "keep.ctor.transplanted"), childNames(group))
  }

  @Test
  fun pendingAnchorConstraintsSurviveTransplant() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.pending.a",
             groupDescriptor(groupElement("keep.pending.group", children = listOf(actionElement("keep.pending.head"))))),
      module("keep.pending.b",
             actionDescriptor(actionElement("keep.pending.anchored",
                                            addToGroupElement("keep.pending.group",
                                                              anchor = "after",
                                                              relativeToAction = "keep.pending.future")))),
    )
    assertEquals(listOf("keep.pending.head", "keep.pending.anchored"), childNames(fixture.group("keep.pending.group")))
    val replaced = fixture.group("keep.pending.group")

    fixture.register(
      module("keep.pending.c", groupDescriptor(groupElement("keep.pending.group", overrides = true, keepContent = true))),
    )
    val replacement = fixture.group("keep.pending.group")
    assertNotSame(replaced, replacement)

    fixture.register(
      module("keep.pending.d",
             actionDescriptor(actionElement("keep.pending.future",
                                            addToGroupElement("keep.pending.group", anchor = "first")))),
    )
    assertEquals(listOf("keep.pending.future", "keep.pending.anchored", "keep.pending.head"), childNames(replacement))
  }

  @Test
  fun stubOverrideStagesTransplantAndReplaysOnUnstub() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.stub.a",
             groupDescriptor(groupElement("keep.stub.group",
                                          children = listOf(actionElement("keep.stub.first"),
                                                            actionElement("keep.stub.second"))))),
      module("keep.stub.b",
             groupDescriptor(groupElement("keep.stub.group",
                                          className = PlainGroup::class.java.name,
                                          overrides = true,
                                          keepContent = true))),
    )

    val staged = fixture.action("keep.stub.group")
    assertTrue(staged is ActionGroupStub, "expected staged ActionGroupStub, got $staged")
    assertEquals(listOf("keep.stub.first", "keep.stub.second"), childNames(staged as DefaultActionGroup))

    val unstubbed = fixture.unstub("keep.stub.group") as DefaultActionGroup
    assertEquals(PlainGroup::class.java, unstubbed.javaClass)
    assertEquals(listOf("keep.stub.first", "keep.stub.second"), childNames(unstubbed))
  }

  @Test
  fun stubOverridePreservesBasePopupThroughUnstub() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.popup.a",
             groupDescriptor(groupElement("keep.popup.group",
                                          popup = true,
                                          children = listOf(actionElement("keep.popup.child"))))),
      module("keep.popup.b",
             groupDescriptor(groupElement("keep.popup.group",
                                          className = PlainGroup::class.java.name,
                                          overrides = true,
                                          keepContent = true))),
    )

    val staged = fixture.action("keep.popup.group") as DefaultActionGroup
    assertTrue(staged.isPopup, "expected transplanted popup flag on the staged stub")

    val unstubbed = fixture.unstub("keep.popup.group") as DefaultActionGroup
    assertEquals(PlainGroup::class.java, unstubbed.javaClass)
    assertTrue(unstubbed.isPopup, "expected popup flag to survive unstub")
  }

  @Test
  fun eagerGroupOverrideReceivesTransplantDirectly() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.eager.a",
             groupDescriptor(groupElement("keep.eager.group", children = listOf(actionElement("keep.eager.child"))))),
      module("keep.eager.b",
             groupDescriptor(groupElement("keep.eager.group", className = DefaultCompactActionGroup::class.java.name,
                                          overrides = true, keepContent = true))),
    )

    val group = fixture.group("keep.eager.group")
    assertEquals(DefaultCompactActionGroup::class.java, group.javaClass)
    assertEquals(listOf("keep.eager.child"), childNames(group))
  }

  @Test
  fun keepContentWithoutOverridesGetsErrorAndGroupIsDropped() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("keep.invalid", groupDescriptor(groupElement("keep.invalid.group", keepContent = true))),
      )
    }

    assertTrue(errors.any { it.contains("\"keep-content\" attribute is allowed only on a group with overrides=\"true\"") },
               "expected keep-content misuse error, got: $errors")
    assertNull(fixture.action("keep.invalid.group"))
  }

  @Test
  fun deferredForwardReferenceResolvesIntoKeepContentReplacement() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("keep.deferred.a",
             groupDescriptor(groupElement("keep.deferred.group",
                                          children = listOf(XmlElement(name = REFERENCE_ELEMENT_NAME,
                                                                       attributes = mapOf(REF_ATTR_NAME to "keep.deferred.target")),
                                                            actionElement("keep.deferred.tail"))))),
      module("keep.deferred.b",
             groupDescriptor(groupElement("keep.deferred.group", overrides = true, keepContent = true))),
      module("keep.deferred.c", actionDescriptor(actionElement("keep.deferred.target"))),
    )

    val group = fixture.group("keep.deferred.group")
    assertEquals(listOf("keep.deferred.target", "keep.deferred.tail"), childNames(group))
    assertSame(fixture.action("keep.deferred.target"), group.childActionsOrStubs[0])
    assertEquals(listOf("keep.deferred.group"), fixture.state.getParentGroupIds("keep.deferred.target"))
  }

  internal class CtorChildrenGroup : DefaultActionGroup() {
    init {
      addAction(NamedAction("keep.ctor.own"), Constraints.LAST) { null }
    }
  }

  internal class PlainGroup : DefaultActionGroup()

  private class RegistrationFixture {
    val state = ActionManagerState()
    private val idToAction = HashMap<String, AnAction>()
    private val registrar = ActionPreInitRegistrar(idToAction, HashMap(), state)

    fun register(vararg modules: IdeaPluginDescriptorImpl) {
      ActionPluginRegistrar().registerActions(descriptors = modules.asSequence(),
                                              keymapToOperations = HashMap(),
                                              actionRegistrar = registrar)
    }

    fun action(id: String): AnAction? = idToAction[id]

    fun group(id: String): DefaultActionGroup = idToAction.getValue(id) as DefaultActionGroup

    fun unstub(id: String): AnAction? = getAction(id = id, canReturnStub = false, actionRegistrar = registrar)
  }

  private fun childNames(group: DefaultActionGroup): List<String> {
    return group.childActionsOrStubs.map { child ->
      if (child is ActionStubBase) child.id else child.templateText ?: child.javaClass.simpleName
    }
  }

  private fun module(pluginId: String, vararg descriptors: ActionElement): IdeaPluginDescriptorImpl {
    val builder = PluginDescriptorBuilder.builder()
    builder.id = pluginId
    descriptors.forEach { builder.addAction(it) }
    return PluginMainDescriptor(raw = builder.build(), pluginPath = Path.of(pluginId), isBundled = true)
  }

  private fun actionDescriptor(element: XmlElement): ActionDescriptorAction {
    return ActionDescriptorAction(className = TestAction::class.java.name, isInternal = false, element = element, resourceBundle = null)
  }

  private fun groupDescriptor(element: XmlElement): ActionElementGroup {
    return ActionElementGroup(className = element.attributes[CLASS_ATTR_NAME],
                              id = element.attributes[ID_ATTR_NAME],
                              element = element,
                              resourceBundle = null)
  }

  private fun groupElement(
    id: String,
    className: String? = null,
    overrides: Boolean = false,
    keepContent: Boolean = false,
    popup: Boolean? = null,
    children: List<XmlElement> = emptyList(),
  ): XmlElement {
    val attributes = HashMap<String, String>()
    attributes[ID_ATTR_NAME] = id
    className?.let { attributes[CLASS_ATTR_NAME] = it }
    popup?.let { attributes["popup"] = it.toString() }
    if (overrides) {
      attributes[OVERRIDES_ATTR_NAME] = "true"
    }
    if (keepContent) {
      attributes[KEEP_CONTENT_ATTR_NAME] = "true"
    }
    return XmlElement(name = GROUP_ELEMENT_NAME, attributes = attributes, children = children)
  }

  private fun actionElement(id: String, vararg children: XmlElement): XmlElement {
    return XmlElement(name = ACTION_ELEMENT_NAME,
                      attributes = mapOf(ID_ATTR_NAME to id, CLASS_ATTR_NAME to TestAction::class.java.name),
                      children = children.toList())
  }

  private fun addToGroupElement(groupId: String, anchor: String? = null, relativeToAction: String? = null): XmlElement {
    val attributes = HashMap<String, String>()
    attributes[GROUP_ID_ATTR_NAME] = groupId
    anchor?.let { attributes["anchor"] = it }
    relativeToAction?.let { attributes["relative-to-action"] = it }
    return XmlElement(name = ADD_TO_GROUP_ELEMENT_NAME, attributes = attributes)
  }

  private fun collectLoggedErrors(action: () -> Unit): List<String> {
    val messages = ArrayList<String>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        messages.add(listOfNotNull(message, t?.message).joinToString(" | "))
        return emptySet()
      }
    }
    val token = LoggedErrorProcessor.executeWith(processor)
    try {
      action()
    }
    finally {
      token.finish()
    }
    return messages
  }

  private class NamedAction(text: String) : AnAction(text) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  private class TestAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
    }
  }
}
