// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.ContainerListener
import javax.swing.JPanel

class ClientPropertyRecursiveTest {
  private val key = Key.create<String>("ClientPropertyRecursiveTest.key")

  @Test
  fun `puts the value into the existing subtree`() {
    val root = CountingPanel()
    val child = CountingPanel()
    val grandChild = CountingPanel()
    root.add(child)
    child.add(grandChild)

    ClientProperty.putRecursive(root, key, "a")

    assertThat(listOf(root, child, grandChild).map { ClientProperty.get(it, key) }).containsOnly("a")
  }

  @Test
  fun `puts the value into a subtree that is added later`() {
    val root = CountingPanel()
    ClientProperty.putRecursive(root, key, "a")

    val child = CountingPanel()
    val grandChild = CountingPanel()
    child.add(grandChild)
    root.add(child)
    val lateGrandChild = CountingPanel()
    child.add(lateGrandChild)

    assertThat(listOf(child, grandChild, lateGrandChild).map { ClientProperty.get(it, key) }).containsOnly("a")
  }

  @Test
  fun `an added child does not walk its siblings again`() {
    val root = CountingPanel()
    ClientProperty.putRecursive(root, key, "a")
    val children = List(CHILD_COUNT) { CountingPanel() }

    children.forEach(root::add)

    assertThat(root.listenerAdds).isEqualTo(1)
    assertThat(children.map { it.listenerAdds }).containsOnly(1)
    assertThat(children.map { ClientProperty.get(it, key) }).containsOnly("a")
  }

  @Test
  fun `a nested value stays when a sibling is added to the outer container`() {
    val root = CountingPanel()
    val inner = CountingPanel()
    val innerChild = CountingPanel()
    root.add(inner)
    inner.add(innerChild)
    ClientProperty.putRecursive(root, key, "outer")
    ClientProperty.putRecursive(inner, key, "inner")

    root.add(CountingPanel())
    val lateInnerChild = CountingPanel()
    inner.add(lateInnerChild)

    assertThat(listOf(inner, innerChild, lateInnerChild).map { ClientProperty.get(it, key) }).containsOnly("inner")
    assertThat(ClientProperty.get(root, key)).isEqualTo("outer")
  }

  @Test
  fun `a moved subtree takes the value of the new parent`() {
    val first = CountingPanel()
    val second = CountingPanel()
    ClientProperty.putRecursive(first, key, "first")
    ClientProperty.putRecursive(second, key, "second")
    val child = CountingPanel()
    val grandChild = CountingPanel()
    child.add(grandChild)
    first.add(child)

    second.add(child)
    val lateGrandChild = CountingPanel()
    child.add(lateGrandChild)

    assertThat(listOf(child, grandChild, lateGrandChild).map { ClientProperty.get(it, key) }).containsOnly("second")
    assertThat(child.containerListeners).hasSize(1)
  }

  @Test
  fun `a new value replaces the old value and the old listener`() {
    val root = CountingPanel()
    val child = CountingPanel()
    root.add(child)
    ClientProperty.putRecursive(root, key, "a")

    ClientProperty.putRecursive(root, key, "b")
    val lateChild = CountingPanel()
    root.add(lateChild)

    assertThat(listOf(root, child, lateChild).map { ClientProperty.get(it, key) }).containsOnly("b")
    assertThat(root.containerListeners).hasSize(1)
    assertThat(child.containerListeners).hasSize(1)
  }

  @Test
  fun `removal clears the value and the listener of the component`() {
    val root = CountingPanel()
    ClientProperty.putRecursive(root, key, "a")

    ClientProperty.removeRecursive(root, key)
    val lateChild = CountingPanel()
    root.add(lateChild)

    assertThat(ClientProperty.get(root, key)).isNull()
    assertThat(ClientProperty.get(lateChild, key)).isNull()
    assertThat(root.containerListeners).isEmpty()
  }

  private class CountingPanel : JPanel() {
    var listenerAdds = 0

    override fun addContainerListener(l: ContainerListener?) {
      listenerAdds++
      super.addContainerListener(l)
    }
  }

  private companion object {
    const val CHILD_COUNT = 50
  }
}
