// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test

@Suppress("UsePropertyAccessSyntax")
class IconLoaderTest {
  @Test
  fun reflectivePath() {
    assertThat(IconLoader.isReflectivePath("AllIcons.Actions.Diff")).isTrue()
    assertThat(IconLoader.isReflectivePath("com.intellij.kubernetes.KubernetesIcons.Kubernetes_Y")).isTrue()
  }
}