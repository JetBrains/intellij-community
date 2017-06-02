/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.tests

import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeType
import com.intellij.testGuiFramework.remote.RemoteTestCase
import org.junit.Test

/**
 * @author Sergey Karashevich
 */
class ControlTest: RemoteTestCase() {

  @Test
  fun testColorSchemeTest() {

    val ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0) // if path is not specified than run current IntelliJ IDEA from compiled sources

    startAndClose(ide, "localhost", 5009, "/Users/jetbrains/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/172.2300/IntelliJ IDEA 2017.2 EAP.app") {
//    startIde(ide, "localhost", 5009) {
//      runTest("com.intellij.testGuiFramework.tests.ColorSchemeTest#testFail")
      runTest("com.intellij.testGuiFramework.tests.ColorSchemeTest#testColorScheme")
      runTest("com.intellij.testGuiFramework.tests.ColorSchemeTest#testColorScheme2")
    }
  }

}