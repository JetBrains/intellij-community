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

import com.intellij.openapi.editor.markup.TextAttributes
import com.jetbrains.reactivemodel.mapping.KDM
import com.jetbrains.reactivemodel.mapping.Mapper
import com.jetbrains.reactivemodel.mapping.Mapping
import com.jetbrains.reactivemodel.mapping.Original
import java.awt.Color

@Mapping(javaClass<TextAttributes>(), mapper = javaClass<TextAttributesMapper>())
data class TextAttributesBean(
    val color: String?,
    val backgroundColor: String?,
    val fontWeight: String?,
    val fontStyle: String?
)

class TextAttributesMapper : Mapper<TextAttributes, TextAttributesBean> {
  override fun map(obj: TextAttributes): TextAttributesBean =
      TextAttributesBean(KDM.map(obj.getForegroundColor()),
          KDM.map(obj.getBackgroundColor()),
          if ((obj.getFontType() and java.awt.Font.BOLD) != 0) "bold" else null,
          if ((obj.getFontType() and java.awt.Font.ITALIC) != 0) "italic" else null)
}
