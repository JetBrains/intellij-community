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
import javax.swing.JComponent

/**
 * An abstract class for global context such as WelcomeFrame, Dialog, IdeFrame. The main different from
 *
 * @author Sergey Karashevich
 */
abstract class GlobalContextCodeGenerator<C : Component> : ContextCodeGenerator<C>{

  override fun priority(): Int = 0 // prioritize component code generators 0 - for common, (n) - for the most specific
  fun generateCode(cmp: Component): String {
    return generate(typeSafeCast(cmp))
  }

  override fun buildContext(component: Component): Context = Context(originalGenerator = this, component = (component as JComponent).rootPane.parent, code = generate(
    typeSafeCast(component.rootPane.parent)))

}

