// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.maddyhome.idea.copyright

import com.intellij.configurationStore.SerializableScheme
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import com.maddyhome.idea.copyright.pattern.EntityUtil
import org.jdom.Element

@Suppress("SpellCheckingInspection")
@JvmField
val DEFAULT_COPYRIGHT_NOTICE: String = EntityUtil.encode(
  "Copyright (c) \$originalComment.match(\"Copyright \\(c\\) (\\d+)\", 1, \"-\", \"\$today.year\")\$today.year. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n" +
  "Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan. \n" +
  "Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna. \n" +
  "Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus. \n" +
  "Vestibulum commodo. Ut rhoncus gravida arcu. ")
class CopyrightProfile @JvmOverloads constructor(profileName: String? = null) : ExternalizableScheme, BaseState(), SerializableScheme {
  // ugly name to preserve compatibility
  // must be not private because otherwise binding is not created for private accessor
  @get:OptionTag("myName")
  var profileName: String? by string()

  var notice: String? by string(DEFAULT_COPYRIGHT_NOTICE)
  var keyword: String? by string(EntityUtil.encode("Copyright"))
  var allowReplaceRegexp: String? by string()

  @Deprecated("use allowReplaceRegexp instead", ReplaceWith(""))
  var allowReplaceKeyword: String? by string()

  init {
    // otherwise will be as default value and name will be not serialized
    this.profileName = profileName
  }

  // ugly name to preserve compatibility
  @Transient
  @NlsSafe
  override fun getName(): String = profileName ?: ""

  override fun setName(value: String) {
    profileName = value
  }

  override fun toString(): String = profileName ?: ""

  override fun writeScheme(): Element {
    val element = Element("copyright")
    serializeObjectInto(this, element)
    return element
  }
}
