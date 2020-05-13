// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun html(body: BODY.() -> Unit) = createHTML(false).html { body { body(this) } }

var TABLE.cellpading: String
  get() = attributes["cellpadding"] ?: ""
  set(value) {
    attributes["cellpadding"] = value
  }

var TABLE.cellspacing: String
  get() = attributes["cellspacing"] ?: ""
  set(value) {
    attributes["cellspacing"] = value
  }

var TD.valign: String
  get() = attributes["valign"] ?: ""
  set(value) {
    attributes["valign"] = value
  }

fun FlowContent.nbsp() = +"&nbsp;"
