/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      panel {
        props = Panel(layout = DummyLayout)
        label {
          props = Label("my label")
        }
      }
    }
    mount(Disposer.newDisposable(), buildElement<Unit> {
      c { props = Unit }
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
      panel {
        props = Panel(layout = DummyLayout)
        label {
          props = Label(text.value)
        }
      }
    }

    mount(Disposer.newDisposable(), buildElement<Unit> {
      c { props = Unit }
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
      panel {
        props = Panel(layout = DummyLayout)
        for (i in 0..count.value)
        label {
          key = i
          props = Label("label $i")
        }
      }
    }

    mount(Disposer.newDisposable(), buildElement<Unit> {
      c { props = Unit }
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
      panel {
        controlReconcile++
        inc = { cp.c.value++ }
        dec = { cp.c.value-- }
        props = Panel(DummyLayout)

        button {
          props = Button(text = "Inc",
                         onClick = inc)
        }
        button {
          props = Button(text = "Dec",
                         onClick = dec)
        }
      }
    }


    val rootComponent = component<Unit>("root") { u, ch ->
      rootReconcile++
      masterCbToggle = { cbEnabled.value = it }
      cbChange = { cbSelected.value = it }
      panel {
        props = Panel(DummyLayout)
        label {
          key = "label"
          props = Label(text = if (allOn.value) "ON" else "OFF")
        }
        checkbox {
          key = "master"
          props = Checkbox(text = "enabled",
                           selected = cbEnabled.value,
                           onChange = masterCbToggle)
        }
        for (i in 0..counter.value) {
          checkbox {
            key = i
            props = Checkbox(text = "$i",
                             selected = cbSelected.value,
                             enabled = cbEnabled.value,
                             onChange = cbChange)
          }
        }
        controls {
          key = "controls"
          props = ControlProps(c = counter)
        }
      }
    }

    mount(Disposer.newDisposable(), buildElement<Unit> {
      rootComponent { props = Unit }
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