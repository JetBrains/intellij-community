// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.icons.EMPTY_ICON
import java.util.function.Supplier

class LineMarkerTest: LightPlatformTestCase() {
  fun testProvidersAreDescriptive() {
    val whiteList = setOf("com.intellij.html.HtmlLineMarkerProvider", "com.intellij.docker.dockerFile.DockerFileStageSeparatorProvider")
    LineMarkerProviders.EP_NAME.extensions.filter {
      it.implementationClass.startsWith("com.intellij") &&
      !it.implementationClass.startsWith("com.intellij.sql") &&
      !whiteList.contains(it.implementationClass)
    }.forEach {
      assertTrue(it.implementationClass, it.instance is LineMarkerProviderDescriptor)
    }
  }

  fun testNullNavigation() {
    val file = PsiFileFactory.getInstance(project).createFileFromText("foo.txt", PlainTextFileType.INSTANCE, "")
    val markerInfo = LineMarkerInfo(file, TextRange.EMPTY_RANGE, EMPTY_ICON, null, null, GutterIconRenderer.Alignment.CENTER,
                                    Supplier { "" })
    assertNull(markerInfo.createGutterRenderer().clickAction)
  }
}