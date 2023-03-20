// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

internal fun ContainerFixture.dialog(
  title: String,
  timeout: Duration = Duration.ofSeconds(20),
  function: DialogFixture.() -> Unit = {}): DialogFixture = step("Search for dialog with title $title") {
  find<DialogFixture>(DialogFixture.byTitle(title), timeout).apply(function)
}

@FixtureName("Dialog")
internal class DialogFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

  companion object {
    @JvmStatic
    fun byTitle(title: String) = byXpath("title $title", "//div[@title='$title' and @class='MyDialog']")
  }

  val title: String
    get() = callJs("component.getTitle();")
}