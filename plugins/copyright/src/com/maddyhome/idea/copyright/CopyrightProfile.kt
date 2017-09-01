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
package com.maddyhome.idea.copyright

import com.intellij.configurationStore.SerializableScheme
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import com.maddyhome.idea.copyright.pattern.EntityUtil
import org.jdom.Element

@JvmField
val DEFAULT_COPYRIGHT_NOTICE: String = EntityUtil.encode(
  "Copyright (c) \$today.year. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n" +
  "Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan. \n" +
  "Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna. \n" +
  "Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus. \n" +
  "Vestibulum commodo. Ut rhoncus gravida arcu. ")

class CopyrightProfile @JvmOverloads constructor(profileName: String? = null) : ExternalizableScheme, BaseState(), SerializableScheme {
  // ugly name to preserve compatibility
  // must be not private because otherwise binding is not created for private accessor
  @get:OptionTag("myName")
  private var profileName by string()

  var notice by string(DEFAULT_COPYRIGHT_NOTICE)
  var keyword by string(EntityUtil.encode("Copyright"))
  var allowReplaceRegexp by string()

  @Deprecated("use allowReplaceRegexp instead", ReplaceWith(""))
  var allowReplaceKeyword by string()

  init {
    // otherwise will be as default value and name will be not serialized
    this.profileName = profileName
  }

  @Transient
  override fun getName() = profileName ?: ""

  override fun setName(value: String) {
    profileName = value
  }

  override fun toString() = profileName ?: ""

  override fun writeScheme(): Element {
    val element = Element("copyright")
    serializeObjectInto(this, element)
    return element
  }
}
