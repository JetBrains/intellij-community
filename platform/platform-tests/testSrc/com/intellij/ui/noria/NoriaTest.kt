// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.noria

import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import java.awt.FlowLayout
import kotlin.test.assertTrue

data class TestNode(var elementType: String,
                    var props: Any,
                    var children: MutableList<TestNode> = arrayListOf(),
                    var disposed: Boolean = false)

class TestToolkit : Toolkit<TestNode> {
  override fun isPrimitive(e: ElementType): Boolean = e.type == "panel" || e.type == "checkbox" || e.type == "label" || e.type == "button"

  override fun createNode(e: Element): TestNode = TestNode(e.type.type as String, e.props, arrayListOf())

  override fun performUpdates(l: List<Update<TestNode>>, root: TestNode) {
    for (update in l) {
      when (update) {
        is UpdateProps -> {
          assertTrue { update.node.props == update.oldProps }
          update.node.props = update.newProps
        }
        is AddChild -> {
          update.parent.children.add(update.index, update.child)
        }
        is RemoveChild -> {
          update.parent.children.remove(update.child)
        }
        is DestroyNode -> {
          update.node.disposed = true
        }
      }
    }
  }
}

private val DummyLayout = FlowLayout()

class NoriaTest : TestCase() {
  fun `test component building`() {
    val root = TestNode("panel", Unit)
    val c = component<Unit>("my component") { u, l ->
      panel(Panel(layout = DummyLayout)) {
        label(Label("my label")) {}
      }
    }
    mount(Disposer.newDisposable(), buildElement(Unit) {
      c(Unit) {}
    }, root, TestToolkit())
    assertEquals(TestNode("panel", Unit).apply {
      children = arrayListOf(TestNode("panel", props = Panel(DummyLayout)).apply {
        children = arrayListOf(TestNode("label", props = Label("my label")))
      })
    }, root)
  }

  fun `test updating props`() {
    val root = TestNode("panel", Unit)
    val text = cell("my label")
    val c = component<Unit>("my component") { u, l ->
      panel(Panel(layout = DummyLayout)) {
        label(Label(text.value)) {}
      }
    }

    mount(Disposer.newDisposable(), buildElement(Unit) {
      c(Unit) {}
    }, root, TestToolkit())
    text.value = "updated label"

    assertEquals(TestNode("panel", Unit).apply {
      children = arrayListOf(TestNode("panel", props = Panel(DummyLayout)).apply {
        children = arrayListOf(TestNode("label", props = Label("updated label")))
      })
    }, root)
  }

  fun `test updating children`() {
    val root = TestNode("panel", Unit)

    val count = cell(0)
    val c = component<Unit>("my component") { u, l ->
      panel(Panel(layout = DummyLayout)) {
        for (i in 0..count.value)
        label(Label("label $i")) {
          key = i
        }
      }
    }

    mount(Disposer.newDisposable(), buildElement(Unit) {
      c(Unit) { }
    }, root, TestToolkit())

    assertEquals(TestNode("panel", Unit).apply {
      children = arrayListOf(TestNode("panel", props = Panel(DummyLayout)).apply {
        children = arrayListOf(TestNode("label", props = Label("label 0")))
      })
    }, root)

    count.value++

    assertEquals(TestNode("panel", Unit).apply {
      children = arrayListOf(TestNode("panel", props = Panel(DummyLayout)).apply {
        children = arrayListOf(TestNode("label", props = Label("label 0")), TestNode("label", props = Label("label 1")))
      })
    }, root)
  }

  fun testComplex() {
    val root = TestNode("panel", Unit)
    val counter = cell(1)
    val cbEnabled = cell(true)
    val cbSelected = cell(true)
    val allOn = cell { cbEnabled.value && cbSelected.value && counter.value > 6 }

    data class ControlProps(val c: VarCell<Int>)
    var inc: () -> Unit = {}
    var dec: () -> Unit = {}
    var masterCbToggle: (Boolean) -> Unit = {}
    var cbChange: (Boolean) -> Unit = {}
    var controlReconcile = 0
    var rootReconcile = 0

    val controls = component<ControlProps>("controls") { cp, ch ->
      panel(Panel(DummyLayout)) {
        controlReconcile++
        inc = { cp.c.value++ }
        dec = { cp.c.value-- }

        button(Button(text = "Inc", onClick = inc)) {}
        button(Button(text = "Dec",
                      onClick = dec)) {}
      }
    }


    val rootComponent = component<Unit>("root") { u, ch ->
      rootReconcile++
      masterCbToggle = { cbEnabled.value = it }
      cbChange = { cbSelected.value = it }
      panel(Panel(DummyLayout)) {
        label(Label(text = if (allOn.value) "ON" else "OFF")) {
          key = "label"
        }
        checkbox(Checkbox(text = "enabled",
                           selected = cbEnabled.value,
                           onChange = masterCbToggle)) {
          key = "master"
        }
        for (i in 0..counter.value) {
          checkbox(Checkbox(text = "$i",
                             selected = cbSelected.value,
                             enabled = cbEnabled.value,
                             onChange = cbChange)) {
            key = i
          }
        }
        controls(ControlProps(c = counter)) {
          key = "controls"
        }
      }
    }

    mount(Disposer.newDisposable(), buildElement(Unit) {
      rootComponent(Unit) { }
    }, root, TestToolkit())

    inc()
    inc()
    cbChange(false)
    masterCbToggle(false)

    val gold = TestNode("panel", Unit).apply {
      children = arrayListOf(TestNode("panel", Panel(DummyLayout)).apply {
        children = arrayListOf(TestNode("label", Label("OFF")),
                               TestNode("checkbox", Checkbox(text = "enabled", selected = false, focusable = false, enabled = true, onChange = masterCbToggle)),
                               TestNode("checkbox", Checkbox(text = "0", selected = false, focusable = false, enabled = false, onChange = cbChange)),
                               TestNode("checkbox", Checkbox(text = "1", selected = false, focusable = false, enabled = false, onChange = cbChange)),
                               TestNode("checkbox", Checkbox(text = "2", selected = false, focusable = false, enabled = false, onChange = cbChange)),
                               TestNode("checkbox", Checkbox(text = "3", selected = false, focusable = false, enabled = false, onChange = cbChange)),
                               TestNode("panel", Panel(DummyLayout)).apply {
                                 children = arrayListOf(TestNode("button", Button(text = "Inc", onClick = inc)),
                                                        TestNode("button", Button(text = "Dec", onClick = dec)))
                               })
      })
    }
    assertEquals(gold, root)
    assertEquals(1, controlReconcile)
    assertEquals(5, rootReconcile)
  }
}