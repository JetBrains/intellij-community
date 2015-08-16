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
package com.jetbrains.reactiveidea.mapping

import com.intellij.ui.ColorUtil
import com.jetbrains.reactivemodel.mapping.BeanMapper
import com.jetbrains.reactivemodel.mapping.Mapper
import java.awt.Color

@BeanMapper(from = Color::class, to = String::class)
public class ColorMapper() : Mapper<Color, String> {
  override fun map(obj: Color): String = "#" + ColorUtil.toHex(obj)
}