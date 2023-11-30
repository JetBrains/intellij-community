// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

class LineMarkerTest: LightPlatformTestCase() {
  fun testProvidersAreDescriptive() {
    val whiteList = setOf("com.intellij.html.HtmlLineMarkerProvider", "com.intellij.docker.dockerFile.DockerFileStageSeparatorProvider")
    LineMarkerProviders.EP_NAME.extensions.filter {
      it.implementationClass.startsWith("com.intellij") &&
      !it.implementationClass.startsWith("com.intellij.sql") &&
      !whiteList.contains(it.implementationClass)
    }.forEach {
      TestCase.assertTrue(it.implementationClass, it.instance is LineMarkerProviderDescriptor)
    }
  }
}