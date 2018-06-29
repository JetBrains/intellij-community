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
package com.intellij.testGuiFramework.generators

import java.awt.Component

/**
 * An abstract class for contexts such as ProjectView, ToolWindow, Editor and others.
 *
 * @author Sergey Karashevich
 */
abstract class LocalContextCodeGenerator<C : Component> : ContextCodeGenerator<C> {

  override fun priority(): Int = 0 // prioritize component code generators 0 - for common, (n) - for the most specific
  @Suppress("UNCHECKED_CAST")

  fun generateCode(cmp: Component): String {
    return generate(typeSafeCast(cmp))
  }

  fun findComponentInHierarchy(componentDeepest: Component): Component? {
    val myAcceptor: (Component) -> Boolean = acceptor()
    var curCmp = componentDeepest
    while (!myAcceptor(curCmp) && curCmp.parent != null) curCmp = curCmp.parent
    if (myAcceptor(curCmp)) return curCmp else return null
  }

  // to stop adding more contexts than its in -> ProjectView
  open fun isLastContext(): Boolean = false

  abstract fun acceptor(): (Component) -> Boolean

  override fun accept(cmp: Component): Boolean = (findComponentInHierarchy(cmp) != null)


  override fun buildContext(component: Component): Context =
    Context(originalGenerator = this, component = typeSafeCast(findComponentInHierarchy(component)!!),
            code = generate(typeSafeCast(findComponentInHierarchy(component)!!)))

}


