// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import java.awt.Container

class ContainerFixtureImpl(private val robot: Robot, private val container: Container) : ContainerFixture<Container> {
  override fun target(): Container = container
  override fun robot(): Robot = robot
}