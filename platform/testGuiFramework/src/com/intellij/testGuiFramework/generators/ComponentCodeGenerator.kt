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
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * @author Sergey Karashevich
 */
interface ComponentCodeGenerator<ComponentType: Component> {

    fun priority(): Int = 0 // prioritize component code generators 0 - for common, (n) - for the most specific
    @Suppress("UNCHECKED_CAST")
    fun accept(cmp: Component): Boolean
    fun generate(cmp: ComponentType, me: MouseEvent, cp: Point): String

    fun typeSafeCast(cmp: Component): ComponentType = cmp as ComponentType
    fun generateCode(cmp: Component, me: MouseEvent, cp: Point): String = generate(typeSafeCast(cmp), me, cp)
}

