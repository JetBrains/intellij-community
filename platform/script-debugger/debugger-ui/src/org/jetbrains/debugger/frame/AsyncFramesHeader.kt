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
package org.jetbrains.debugger.frame

import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.frame.XStackFrame

class AsyncFramesHeader(val asyncFunctionName: String) : XStackFrame() {
  override fun customizePresentation(component: ColoredTextContainer) {
    component.append("Async call from ", PREFIX_ATTRIBUTES)
    component.append(asyncFunctionName, NAME_ATTRIBUTES)
  }
}

private val PREFIX_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, UIUtil.getInactiveTextColor())
private val NAME_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC or SimpleTextAttributes.STYLE_BOLD,
                                                   UIUtil.getInactiveTextColor())