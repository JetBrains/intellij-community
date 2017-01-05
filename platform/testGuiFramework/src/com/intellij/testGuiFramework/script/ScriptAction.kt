/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.script

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.impl.GuiTestCase
import org.fest.swing.core.FastRobot

/**
 * @author Sergey Karashevich
 */
class ScriptAction(val scriptName: String) : AnAction("guiTest." + scriptName) {

  override fun actionPerformed(e: AnActionEvent?) {

    val myPackage = "com.intellij.testGuiFramework.tests"
    val loadedClass = this.javaClass.classLoader.loadClass("${myPackage}.${scriptName}")
    val guiTest = loadedClass.newInstance() as GuiTestCase

    //setting advanced robot
    guiTest.setRobot(FastRobot())

    val testsInClass = loadedClass.methods.filter { it.name.contains("test") }
    ApplicationManager.getApplication().executeOnPooledThread { testsInClass.get(0).invoke(guiTest) }
  }

}
