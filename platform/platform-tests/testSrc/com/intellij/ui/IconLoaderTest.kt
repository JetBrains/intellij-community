// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.ui.EmptyIcon
import org.junit.Test
import javax.swing.Icon

class IconLoaderTest {
  @Test
  fun reflectivePathCheck() {
    assertThat(IconLoader.isReflectivePath("AllIcons.Actions.Diff")).isTrue()
    assertThat(IconLoader.isReflectivePath("com.intellij.kubernetes.KubernetesIcons.Kubernetes_Y")).isTrue()
  }

  @Test
  fun reflectivePathNestedClass() {
    assertThat(IconLoader.getReflectiveIcon("com.intellij.ui.TestIcons.TestNestedIcons.ToolWindow",
                                            TestIcons.TestNestedIcons::class.java.classLoader))
      .isEqualTo(TestIcons.TestNestedIcons.ToolWindow)
  }

  @Test
  fun reflectivePathNestedClassWithDollar() {
    assertThat(IconLoader.getReflectiveIcon("com.intellij.ui.TestIcons\$TestNestedIcons.ToolWindow",
                                            TestIcons.TestNestedIcons::class.java.classLoader))
      .isEqualTo(TestIcons.TestNestedIcons.ToolWindow)
  }

  @Test
  fun reflectivePath() {
    assertThat(IconLoader.getReflectiveIcon("com.intellij.ui.TestIcons.NonNested",
                                            TestIcons::class.java.classLoader))
      .isEqualTo(TestIcons.NonNested)
  }

  @Test
  fun reflectivePathAllIcons() {
    assertThat(IconLoader.getReflectiveIcon("AllIcons.FileTypes.AddAny",
                                            TestIcons::class.java.classLoader))
      .isEqualTo(AllIcons.FileTypes.AddAny)
  }
}

internal class TestIcons {
  companion object {
    @JvmField val NonNested: Icon = EmptyIcon.create(21)
  }

  class TestNestedIcons {
    companion object {
      @JvmField val ToolWindow: Icon = EmptyIcon.create(42)
    }
  }
}