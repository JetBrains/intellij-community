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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.File

/**
 * Created by jetbrains on 12/09/16.
 */
class ScriptRunnerAction() : AnAction(){

  override fun actionPerformed(e: AnActionEvent?) {
    val myPackage = "com.intellij.tests.gui.test"
    val packagePath =  myPackage.replace("", "/")
    val testFolder = this.javaClass.classLoader.getResource(packagePath)
    val testFiles = File(testFolder.file).listFiles().filter { !it.name.contains("$") } //remove inlined classes
    val actionManager = ActionManager.getInstance()

    testFiles.forEach {
      val testName = it.name.substring(0, it.name.indexOf('.'))
      val genId = "guiTest." + testName
      val script = com.intellij.testGuiFramework.script.ScriptAction(testName)
      if (actionManager.getAction(genId) != null)
        actionManager.unregisterAction(genId)
      actionManager.registerAction(genId, script)
    }
  }

}