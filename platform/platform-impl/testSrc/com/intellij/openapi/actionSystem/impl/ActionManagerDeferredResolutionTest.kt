// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionStub
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionDescriptorAction
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementGroup
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementName
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xml.dom.XmlElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
@Suppress("UnstableApiUsage")
internal class ActionManagerDeferredResolutionTest {
  @Test
  fun forwardReferenceInsideGroupResolvesAtEndOfRegistrationPreservingSlot() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("deferred.slot.a",
             groupDescriptor(groupElement("deferred.slot.group",
                                          actionElement("deferred.slot.first"),
                                          referenceElement("deferred.slot.target"),
                                          actionElement("deferred.slot.last")))),
      module("deferred.slot.b", actionDescriptor(actionElement("deferred.slot.target"))),
    )

    val group = fixture.group("deferred.slot.group")
    assertEquals(listOf("deferred.slot.first", "deferred.slot.target", "deferred.slot.last"), childIds(group))
    assertSame(fixture.action("deferred.slot.target"), group.childActionsOrStubs[1])
    assertEquals(listOf("deferred.slot.group"), fixture.state.getParentGroupIds("deferred.slot.target"))
  }

  @Test
  fun anchorsMatchDeferredReferenceSlotBeforeItResolves() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("deferred.anchor.a",
             groupDescriptor(groupElement("deferred.anchor.group",
                                          referenceElement("deferred.anchor.target"),
                                          actionElement("deferred.anchor.tail")))),
      module("deferred.anchor.b",
             actionDescriptor(actionElement("deferred.anchor.neighbor",
                                            addToGroupElement("deferred.anchor.group",
                                                              anchor = "before",
                                                              relativeToAction = "deferred.anchor.target")))),
      module("deferred.anchor.c", actionDescriptor(actionElement("deferred.anchor.target"))),
    )

    assertEquals(listOf("deferred.anchor.neighbor", "deferred.anchor.target", "deferred.anchor.tail"),
                 childIds(fixture.group("deferred.anchor.group")))
  }

  @Test
  fun forwardAddToGroupLandsAtEndOfRegistration() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("deferred.add.a",
             actionDescriptor(actionElement("deferred.add.action", addToGroupElement("deferred.add.group")))),
      module("deferred.add.b", groupDescriptor(groupElement("deferred.add.group"))),
    )

    val group = fixture.group("deferred.add.group")
    assertSame(fixture.action("deferred.add.action"), group.childActionsOrStubs.single())
    assertEquals(listOf("deferred.add.group"), fixture.state.getParentGroupIds("deferred.add.action"))
  }

  @Test
  fun forwardTopLevelReferenceProcessesAddToGroupAtEndOfRegistration() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("deferred.toplevel.a",
             groupDescriptor(groupElement("deferred.toplevel.group")),
             referenceDescriptor(referenceElement("deferred.toplevel.target", addToGroupElement("deferred.toplevel.group")))),
      module("deferred.toplevel.b", actionDescriptor(actionElement("deferred.toplevel.target"))),
    )

    val group = fixture.group("deferred.toplevel.group")
    assertSame(fixture.action("deferred.toplevel.target"), group.childActionsOrStubs.single())
  }

  @Test
  fun riderOnForwardReferenceKeepsParsePositionAmongParseTimeAdders() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("rider.order.a", groupDescriptor(groupElement("rider.order.group"))),
      module("rider.order.b",
             referenceDescriptor(referenceElement("rider.order.target", addToGroupElement("rider.order.group"))),
             actionDescriptor(actionElement("rider.order.neighbor", addToGroupElement("rider.order.group")))),
      module("rider.order.c", actionDescriptor(actionElement("rider.order.target"))),
    )

    val group = fixture.group("rider.order.group")
    assertEquals(listOf("rider.order.target", "rider.order.neighbor"), childIds(group))
    assertSame(fixture.action("rider.order.target"), group.childActionsOrStubs[0])
    assertEquals(listOf("rider.order.group"), fixture.state.getParentGroupIds("rider.order.target"))
  }

  @Test
  fun parseTimeAdderBeforeForwardReferenceRiderKeepsDocumentOrder() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("rider.docorder.a", groupDescriptor(groupElement("rider.docorder.group"))),
      module("rider.docorder.b",
             actionDescriptor(actionElement("rider.docorder.neighbor", addToGroupElement("rider.docorder.group"))),
             referenceDescriptor(referenceElement("rider.docorder.target", addToGroupElement("rider.docorder.group")))),
      module("rider.docorder.c", actionDescriptor(actionElement("rider.docorder.target"))),
    )

    assertEquals(listOf("rider.docorder.neighbor", "rider.docorder.target"), childIds(fixture.group("rider.docorder.group")))
  }

  @Test
  fun anchorOnForwardReferenceRiderIsHonoredViaStub() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("rider.anchor.a",
             groupDescriptor(groupElement("rider.anchor.group", actionElement("rider.anchor.head")))),
      module("rider.anchor.b",
             referenceDescriptor(referenceElement("rider.anchor.target",
                                                  addToGroupElement("rider.anchor.group", anchor = "first")))),
      module("rider.anchor.c", actionDescriptor(actionElement("rider.anchor.target"))),
    )

    assertEquals(listOf("rider.anchor.target", "rider.anchor.head"), childIds(fixture.group("rider.anchor.group")))
  }

  @Test
  fun riderOnInGroupForwardReferenceKeepsTargetGroupParseOrder() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ingroup.rider.a", groupDescriptor(groupElement("ingroup.rider.target.group"))),
      module("ingroup.rider.b",
             groupDescriptor(groupElement("ingroup.rider.host",
                                          referenceElement("ingroup.rider.target",
                                                           addToGroupElement("ingroup.rider.target.group")))),
             actionDescriptor(actionElement("ingroup.rider.neighbor", addToGroupElement("ingroup.rider.target.group")))),
      module("ingroup.rider.c", actionDescriptor(actionElement("ingroup.rider.target"))),
    )

    assertEquals(listOf("ingroup.rider.target"), childIds(fixture.group("ingroup.rider.host")))
    assertEquals(listOf("ingroup.rider.target", "ingroup.rider.neighbor"),
                 childIds(fixture.group("ingroup.rider.target.group")))
    assertSame(fixture.action("ingroup.rider.target"), fixture.group("ingroup.rider.target.group").childActionsOrStubs[0])
  }

  @Test
  fun anchorOnInGroupForwardReferenceRiderIsHonoredViaStub() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ingroup.anchor.a",
             groupDescriptor(groupElement("ingroup.anchor.target.group", actionElement("ingroup.anchor.head")))),
      module("ingroup.anchor.b",
             groupDescriptor(groupElement("ingroup.anchor.host",
                                          referenceElement("ingroup.anchor.target",
                                                           addToGroupElement("ingroup.anchor.target.group", anchor = "first"))))),
      module("ingroup.anchor.c", actionDescriptor(actionElement("ingroup.anchor.target"))),
    )

    assertEquals(listOf("ingroup.anchor.target", "ingroup.anchor.head"),
                 childIds(fixture.group("ingroup.anchor.target.group")))
  }

  @Test
  fun inGroupTopLevelAndParseTimeAddersLandInDocumentOrder() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("mixed.order.a", groupDescriptor(groupElement("mixed.order.group"))),
      module("mixed.order.b",
             actionDescriptor(actionElement("mixed.order.first", addToGroupElement("mixed.order.group"))),
             groupDescriptor(groupElement("mixed.order.host",
                                          referenceElement("mixed.order.second",
                                                           addToGroupElement("mixed.order.group")))),
             referenceDescriptor(referenceElement("mixed.order.third", addToGroupElement("mixed.order.group")))),
      module("mixed.order.c",
             actionDescriptor(actionElement("mixed.order.second")),
             actionDescriptor(actionElement("mixed.order.third"))),
    )

    assertEquals(listOf("mixed.order.first", "mixed.order.second", "mixed.order.third"),
                 childIds(fixture.group("mixed.order.group")))
  }

  @Test
  fun stagedAddToGroupFlushesAtGroupRegistrationSoLaterAnchorFirstWins() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("flush.b4.core",
             actionDescriptor(actionElement("flush.b4.early", addToGroupElement("flush.b4.group", anchor = "first")))),
      module("flush.b4.lang",
             groupDescriptor(groupElement("flush.b4.group", actionElement("flush.b4.base")))),
      module("flush.b4.python",
             actionDescriptor(actionElement("flush.b4.late", addToGroupElement("flush.b4.group", anchor = "first")))),
    )

    assertEquals(listOf("flush.b4.late", "flush.b4.early", "flush.b4.base"), childIds(fixture.group("flush.b4.group")))
  }

  @Test
  fun stagedAddToGroupsFlushInStagingFifoOrderAheadOfLaterParsers() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("flush.fifo.a",
             actionDescriptor(actionElement("flush.fifo.one", addToGroupElement("flush.fifo.group"))),
             actionDescriptor(actionElement("flush.fifo.two", addToGroupElement("flush.fifo.group")))),
      module("flush.fifo.b", groupDescriptor(groupElement("flush.fifo.group"))),
      module("flush.fifo.c",
             actionDescriptor(actionElement("flush.fifo.three", addToGroupElement("flush.fifo.group")))),
    )

    assertEquals(listOf("flush.fifo.one", "flush.fifo.two", "flush.fifo.three"), childIds(fixture.group("flush.fifo.group")))
  }

  @Test
  fun stagedRiderWithUnresolvedActionInsertsStubAtFlushPosition() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("flush.stub.a",
             referenceDescriptor(referenceElement("flush.stub.target", addToGroupElement("flush.stub.group")))),
      module("flush.stub.b", groupDescriptor(groupElement("flush.stub.group"))),
      module("flush.stub.c",
             actionDescriptor(actionElement("flush.stub.neighbor", addToGroupElement("flush.stub.group")))),
      module("flush.stub.d", actionDescriptor(actionElement("flush.stub.target"))),
    )

    val group = fixture.group("flush.stub.group")
    assertEquals(listOf("flush.stub.target", "flush.stub.neighbor"), childIds(group))
    assertSame(fixture.action("flush.stub.target"), group.childActionsOrStubs[0])
  }

  @Test
  fun stagedOverrideAppliesWhenBaseRegistersKeepingIdAndSlot() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.basic.product",
             actionDescriptor(actionElement("ovr.basic.target",
                                            className = OverridingTestAction::class.java.name,
                                            overrides = true))),
      module("ovr.basic.content",
             groupDescriptor(groupElement("ovr.basic.group",
                                          actionElement("ovr.basic.head"),
                                          actionElement("ovr.basic.target")))),
    )

    val group = fixture.group("ovr.basic.group")
    assertEquals(listOf("ovr.basic.head", "ovr.basic.target"), childIds(group))
    val target = fixture.action("ovr.basic.target") as ActionStub
    assertEquals(OverridingTestAction::class.java.name, target.className)
    assertSame(target, group.childActionsOrStubs[1])
  }

  @Test
  fun stagedOverrideWithKeepContentTransplantsAccumulatedChildren() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.keep.product",
             groupDescriptor(groupElement("ovr.keep.target",
                                          actionElement("ovr.keep.own"),
                                          overrides = true,
                                          keepContent = true))),
      module("ovr.keep.content",
             groupDescriptor(groupElement("ovr.keep.target", actionElement("ovr.keep.base.child")))),
    )

    assertEquals(listOf("ovr.keep.base.child", "ovr.keep.own"), childIds(fixture.group("ovr.keep.target")))
  }

  @Test
  fun multipleStagedOverridesApplyInStagingOrderSoTheLastWins() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.fifo.p1",
             actionDescriptor(actionElement("ovr.fifo.target",
                                            className = OverridingTestAction::class.java.name,
                                            overrides = true))),
      module("ovr.fifo.p2",
             actionDescriptor(actionElement("ovr.fifo.target",
                                            className = SecondOverridingTestAction::class.java.name,
                                            overrides = true))),
      module("ovr.fifo.base", actionDescriptor(actionElement("ovr.fifo.target"))),
    )

    assertEquals(SecondOverridingTestAction::class.java.name, (fixture.action("ovr.fifo.target") as ActionStub).className)
  }

  @Test
  fun stagedOverrideWithoutBaseKeepsTodaysDiagnostics() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("ovr.missing.product",
               actionDescriptor(actionElement("ovr.missing.target",
                                              className = OverridingTestAction::class.java.name,
                                              overrides = true))),
      )
    }

    assertTrue(errors.any { it.contains("'ovr.missing.target'") && it.contains("does not override anything") },
               "expected override-without-base error, got: $errors")
    assertNull(fixture.action("ovr.missing.target"))
  }

  @Test
  fun unregisterAfterStagedOverrideAppliedRemovesTheOverriddenInstance() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.unreg.product",
             actionDescriptor(actionElement("ovr.unreg.target",
                                            className = OverridingTestAction::class.java.name,
                                            overrides = true))),
      module("ovr.unreg.base", actionDescriptor(actionElement("ovr.unreg.target"))),
      module("ovr.unreg.remover", unregisterDescriptor("ovr.unreg.target")),
    )

    assertNull(fixture.action("ovr.unreg.target"))
  }

  @Test
  fun inGroupStagedOverrideAppliesAtBaseRegistrationKeepingSlot() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.ingroup.product",
             groupDescriptor(groupElement("ovr.ingroup.menu",
                                          actionElement("ovr.ingroup.head"),
                                          actionElement("ovr.ingroup.target",
                                                        className = OverridingTestAction::class.java.name,
                                                        overrides = true),
                                          actionElement("ovr.ingroup.tail")))),
      module("ovr.ingroup.content", actionDescriptor(actionElement("ovr.ingroup.target"))),
    )

    val menu = fixture.group("ovr.ingroup.menu")
    assertEquals(listOf("ovr.ingroup.head", "ovr.ingroup.target", "ovr.ingroup.tail"), childIds(menu))
    val target = fixture.action("ovr.ingroup.target") as ActionStub
    assertEquals(OverridingTestAction::class.java.name, target.className)
    assertSame(target, menu.childActionsOrStubs[1])
  }

  @Test
  fun mixedTopLevelAndInGroupStagedOverridesApplyInStagingOrderSoTheLastWins() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.mixed.p1",
             groupDescriptor(groupElement("ovr.mixed.host",
                                          actionElement("ovr.mixed.target",
                                                        className = OverridingTestAction::class.java.name,
                                                        overrides = true)))),
      module("ovr.mixed.p2",
             actionDescriptor(actionElement("ovr.mixed.target",
                                            className = SecondOverridingTestAction::class.java.name,
                                            overrides = true))),
      module("ovr.mixed.base", actionDescriptor(actionElement("ovr.mixed.target"))),
    )

    val target = fixture.action("ovr.mixed.target") as ActionStub
    assertEquals(SecondOverridingTestAction::class.java.name, target.className)
    assertSame(target, fixture.group("ovr.mixed.host").childActionsOrStubs.single())
  }

  @Test
  fun inGroupStagedOverrideWithoutBaseKeepsErrorAndRetractsPlaceholderSilently() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("ovr.ingroup.missing.product",
               groupDescriptor(groupElement("ovr.ingroup.missing.host",
                                            actionElement("ovr.ingroup.missing.head"),
                                            actionElement("ovr.ingroup.missing.target",
                                                          className = OverridingTestAction::class.java.name,
                                                          overrides = true)))),
      )
    }

    assertTrue(errors.any { it.contains("'ovr.ingroup.missing.target'") && it.contains("does not override anything") },
               "expected override-without-base error, got: $errors")
    assertTrue(errors.none { it.contains("isn't registered") }, "placeholder retraction must stay silent, got: $errors")
    assertEquals(listOf("ovr.ingroup.missing.head"), childIds(fixture.group("ovr.ingroup.missing.host")))
    assertNull(fixture.action("ovr.ingroup.missing.target"))
  }

  @Test
  fun failedStagedInGroupOverrideRetractsPlaceholderInsteadOfPlacingBase() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("ovr.failed.product",
               groupDescriptor(groupElement("ovr.failed.host",
                                            actionElement("ovr.failed.head"),
                                            groupElement("ovr.failed.target", overrides = true)))),
        module("ovr.failed.base", actionDescriptor(actionElement("ovr.failed.target"))),
      )
    }

    assertTrue(errors.any { it.contains("cannot replace a group with an action and vice versa: ovr.failed.target") },
               "expected kind-mismatch error, got: $errors")
    assertEquals(listOf("ovr.failed.head"), childIds(fixture.group("ovr.failed.host")))
    assertNotNull(fixture.action("ovr.failed.target"))
  }

  @Test
  fun inGroupStagedGroupOverrideWithKeepContentTransplantsAccumulatedChildren() {
    val fixture = RegistrationFixture()
    fixture.register(
      module("ovr.ingroup.keep.product",
             groupDescriptor(groupElement("ovr.ingroup.keep.host",
                                          groupElement("ovr.ingroup.keep.target",
                                                       actionElement("ovr.ingroup.keep.own"),
                                                       overrides = true,
                                                       keepContent = true)))),
      module("ovr.ingroup.keep.content",
             groupDescriptor(groupElement("ovr.ingroup.keep.target", actionElement("ovr.ingroup.keep.base.child")))),
    )

    assertEquals(listOf("ovr.ingroup.keep.base.child", "ovr.ingroup.keep.own"), childIds(fixture.group("ovr.ingroup.keep.target")))
    assertSame(fixture.action("ovr.ingroup.keep.target"),
               fixture.group("ovr.ingroup.keep.host").childActionsOrStubs.single())
  }

  @Test
  fun unresolvedReferenceGetsParseTimeDiagnosticsAtEndOfRegistration() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("deferred.missing.a",
               groupDescriptor(groupElement("deferred.missing.group", referenceElement("deferred.missing.target")))),
      )
    }

    assertTrue(errors.any { it.contains("action specified by reference isn't registered (ID=deferred.missing.target)") },
               "expected unresolved-reference error, got: $errors")
    assertEquals(emptyList<String>(), childIds(fixture.group("deferred.missing.group")))
  }

  @Test
  fun unresolvedAddToGroupGetsParseTimeDiagnosticsAtEndOfRegistration() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("deferred.nogroup.a",
               actionDescriptor(actionElement("deferred.nogroup.action", addToGroupElement("deferred.nogroup.group")))),
      )
    }

    assertTrue(errors.any { it.contains("group with id \"deferred.nogroup.group\" isn't registered") },
               "expected unresolved-group error, got: $errors")
    assertNotNull(fixture.action("deferred.nogroup.action"))
  }

  @Test
  fun unregisteredInGroupReferenceTargetRetractsSilently() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("unreg.ref.core",
               groupDescriptor(groupElement("unreg.ref.menu",
                                            actionElement("unreg.ref.head"),
                                            referenceElement("unreg.ref.target")))),
        module("unreg.ref.content",
               actionDescriptor(actionElement("unreg.ref.target", className = UnregisterTargetTestAction::class.java.name))),
        module("unreg.ref.product",
               unregisterDescriptor("unreg.ref.menu"),
               unregisterDescriptor("unreg.ref.target")),
      )
    }

    assertEquals(emptyList<String>(), errors)
    assertNull(fixture.action("unreg.ref.target"))
    assertNull(fixture.action("unreg.ref.menu"))
  }

  @Test
  fun retractedSecondaryReferenceStubLeavesNoSecondaryActionsResidue() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("retract.secondary.a",
               groupDescriptor(groupElement("retract.secondary.group",
                                            referenceElement("retract.secondary.missing", secondary = true)))),
      )
    }

    assertTrue(errors.any { it.contains("action specified by reference isn't registered (ID=retract.secondary.missing)") },
               "expected unresolved-reference error, got: $errors")
    val group = fixture.group("retract.secondary.group")
    assertEquals(emptyList<String>(), childIds(group))
    assertEquals(emptySet<AnAction>(), secondaryActions(group))
  }

  @Test
  fun unregisteredTopLevelReferenceTargetStaysSilent() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("unreg.toplevel.a",
               groupDescriptor(groupElement("unreg.toplevel.group")),
               referenceDescriptor(referenceElement("unreg.toplevel.target", addToGroupElement("unreg.toplevel.group")))),
        module("unreg.toplevel.b",
               actionDescriptor(actionElement("unreg.toplevel.target", className = UnregisterTargetTestAction::class.java.name))),
        module("unreg.toplevel.c", unregisterDescriptor("unreg.toplevel.target")),
      )
    }

    assertEquals(emptyList<String>(), errors)
    assertEquals(emptyList<String>(), childIds(fixture.group("unreg.toplevel.group")))
    assertNull(fixture.action("unreg.toplevel.target"))
  }

  @Test
  fun reRegisteredTargetResolvesDeferredReferenceDespiteEarlierUnregister() {
    val fixture = RegistrationFixture()
    val errors = collectLoggedErrors {
      fixture.register(
        module("unreg.rereg.base",
               actionDescriptor(actionElement("unreg.rereg.target", className = UnregisterTargetTestAction::class.java.name))),
        module("unreg.rereg.remover", unregisterDescriptor("unreg.rereg.target")),
        module("unreg.rereg.consumer",
               groupDescriptor(groupElement("unreg.rereg.menu", referenceElement("unreg.rereg.target")))),
        module("unreg.rereg.replacer", actionDescriptor(actionElement("unreg.rereg.target"))),
      )
    }

    assertEquals(emptyList<String>(), errors)
    assertEquals(listOf("unreg.rereg.target"), childIds(fixture.group("unreg.rereg.menu")))
    assertSame(fixture.action("unreg.rereg.target"), fixture.group("unreg.rereg.menu").childActionsOrStubs.single())
  }

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
  }

  private fun childIds(group: DefaultActionGroup): List<String> = group.childActionsOrStubs.map { (it as ActionStubBase).id }

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

  private fun referenceDescriptor(element: XmlElement): ActionElement.ActionElementMisc {
    return ActionElement.ActionElementMisc(name = ActionElementName.reference, element = element, resourceBundle = null)
  }

  private fun unregisterDescriptor(id: String): ActionElement.ActionElementMisc {
    return ActionElement.ActionElementMisc(name = ActionElementName.unregister,
                                           element = XmlElement(name = "unregister", attributes = mapOf(ID_ATTR_NAME to id)),
                                           resourceBundle = null)
  }

  private fun actionElement(
    id: String,
    vararg children: XmlElement,
    className: String = TestAction::class.java.name,
    overrides: Boolean = false,
  ): XmlElement {
    val attributes = HashMap<String, String>()
    attributes[ID_ATTR_NAME] = id
    attributes[CLASS_ATTR_NAME] = className
    if (overrides) {
      attributes[OVERRIDES_ATTR_NAME] = "true"
    }
    return XmlElement(name = ACTION_ELEMENT_NAME, attributes = attributes, children = children.toList())
  }

  private fun groupElement(
    id: String,
    vararg children: XmlElement,
    overrides: Boolean = false,
    keepContent: Boolean = false,
  ): XmlElement {
    val attributes = HashMap<String, String>()
    attributes[ID_ATTR_NAME] = id
    if (overrides) {
      attributes[OVERRIDES_ATTR_NAME] = "true"
    }
    if (keepContent) {
      attributes["keep-content"] = "true"
    }
    return XmlElement(name = GROUP_ELEMENT_NAME, attributes = attributes, children = children.toList())
  }

  private fun referenceElement(ref: String, vararg children: XmlElement, secondary: Boolean = false): XmlElement {
    val attributes = HashMap<String, String>()
    attributes[REF_ATTR_NAME] = ref
    if (secondary) {
      attributes["secondary"] = "true"
    }
    return XmlElement(name = REFERENCE_ELEMENT_NAME, attributes = attributes, children = children.toList())
  }

  private fun secondaryActions(group: DefaultActionGroup): Set<AnAction> {
    val field = ActionGroup::class.java.getDeclaredField("mySecondaryActions")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return (field.get(group) as? Set<AnAction>).orEmpty()
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

  private class TestAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
    }
  }
}

// top-level so the unregister interplay test can convert the stub reflectively
private class OverridingTestAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
  }
}

private class SecondOverridingTestAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
  }
}

// top-level so <unregister> can convert the target stub reflectively
private class UnregisterTargetTestAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
  }
}
