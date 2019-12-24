// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ElementUtil")

package com.intellij.remote.ext

import org.jdom.Content
import org.jdom.Element

/**
 * Copies attributes and elements from [source] node to [target] node if they
 * are not present in the latter one.
 *
 * Preservers [target] element name.
 *
 * @param source the source element to copy from
 * @param target the target element to copy to
 */
fun copyMissingContent(source: Element, target: Element) {
  val targetClone = target.clone()
  for (attribute in source.attributes) {
    if (!targetClone.hasAttribute(attribute.name)) {
      target.setAttribute(attribute.clone())
    }
  }
  for (content in source.content) {
    if (!targetClone.hasContent(content)) {
      target.addContent(content.clone())
    }
  }
}

private fun Element.hasAttribute(name: String) = getAttribute(name) != null

private fun Element.hasContent(content: Content): Boolean = when (content) {
  is Element -> getChildren(content.name).isNotEmpty()
  else -> false
}
