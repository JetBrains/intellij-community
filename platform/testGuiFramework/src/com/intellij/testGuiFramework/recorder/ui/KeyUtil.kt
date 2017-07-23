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
package com.intellij.testGuiFramework.recorder.ui

import java.awt.event.KeyEvent.*

/**
 * @author Sergey Karashevich
 */
object KeyUtil {

  fun patch(ch: Char) =
    when (ch) {
      '\b' -> getKeyText(VK_BACK_SPACE)
      '\t' -> getKeyText(VK_TAB)
      '\n' -> getKeyText(VK_ENTER)
      '\u0018' -> getKeyText(VK_CANCEL)
      '\u001b' -> getKeyText(VK_ESCAPE)
      '\u007f' -> getKeyText(VK_DELETE)
      else -> ch.toString()
    }
}