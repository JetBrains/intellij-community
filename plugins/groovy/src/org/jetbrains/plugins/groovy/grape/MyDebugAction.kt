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
package org.jetbrains.plugins.groovy.grape

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.JavaPsiFacade

class MyDebugAction : AnAction("Debug action") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val element = CommonDataKeys.PSI_ELEMENT.getData(e.dataContext) ?: return
    val clazz = JavaPsiFacade.getInstance(project).findClass("org.eclipse.jetty.webapp.WebAppContext", element.resolveScope)
    println(clazz)
  }

}