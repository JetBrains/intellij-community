/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.rules.ExternalResource
import javax.swing.SwingUtilities

public fun invokeAndWaitIfNeed(runnable: () -> Unit) {
  if (SwingUtilities.isEventDispatchThread()) runnable() else SwingUtilities.invokeAndWait(runnable)
}

public inline fun runWriteAction(runnable: () -> Unit) {
  val token = WriteAction.start()
  try {
    runnable()
  }
  finally {
    token.finish()
  }
}

public class FixtureRule() : ExternalResource() {
  val projectFixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture()

  override fun before() {
    UsefulTestCase.replaceIdeEventQueueSafely()

    invokeAndWaitIfNeed { projectFixture.setUp() }
  }

  override fun after() {
    invokeAndWaitIfNeed { projectFixture.tearDown() }
  }
}