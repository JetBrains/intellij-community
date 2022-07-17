// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

internal fun RemoteRobot.welcomeFrame(function: WelcomeFrameFixture.() -> Unit) {
  find(WelcomeFrameFixture::class.java, Duration.ofSeconds(10)).apply(function)
}

@FixtureName("Welcome Frame")
@DefaultXpath("type", "//div[@class='FlatWelcomeFrame']")
internal class WelcomeFrameFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot,
                                                                                                                        remoteComponent) {
  val createNewProjectLink
    get() = actionLink(byXpath("New Project",
                               "//div[(@class='MainButton' and @text='New Project') or (@accessiblename='New Project' and @class='JButton')]"))
  val moreActions
    get() = button(byXpath("More Action", "//div[@accessiblename='More Actions']"))

  val heavyWeightPopup
    get() = remoteRobot.find(ComponentFixture::class.java, byXpath("//div[@class='HeavyWeightWindow']"))
}