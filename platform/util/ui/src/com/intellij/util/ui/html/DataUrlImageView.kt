// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.util.ui.html

import com.intellij.util.asSafely
import com.intellij.util.ui.JBImageToolkit
import java.net.URL
import javax.swing.text.Element
import javax.swing.text.html.HTML
import javax.swing.text.html.ImageView

internal open class DataUrlImageView(elem: Element?) : ImageView(elem) {

  override fun getImageURL(): URL? =
    element.attributes.getAttribute(HTML.Attribute.SRC).asSafely<String>()?.let {
      JBImageToolkit.tryBuildDataImageUrl(it)
    } ?: super.getImageURL()

}