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
 * @author Sergey Karashevich
 */
interface ContextCodeGenerator<C : Component> {

  fun priority(): Int
  fun buildContext(component: Component): Context

  fun accept(cmp: Component): Boolean
  fun generate(cmp: C): String
  fun typeSafeCast(cmp: Component): C = cmp as C
  fun closeContext(): String = "}"

}
