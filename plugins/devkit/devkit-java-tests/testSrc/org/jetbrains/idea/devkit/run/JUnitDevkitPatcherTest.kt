// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.util.lang.JavaVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JUnitDevkitPatcherTest : BareTestFixtureTestCase() {
  @Test fun jdk17AddOpens() {
    val jdk = IdeaTestUtil.getMockJdk(JavaVersion.parse("17.0.1"))
    val parametersList = ParametersList()
    JUnitDevKitPatcher.appendAddOpensWhenNeeded(jdk, parametersList)
    @Suppress("SpellCheckingInspection")
    assertThat(parametersList.list)
      .contains("--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/java.time=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
                "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
                "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
                "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED")
      .doesNotContain("--add-opens=java.desktop/sun.awt.${if (SystemInfo.isWindows) "X11" else "windows"}=ALL-UNNAMED")
  }
}
