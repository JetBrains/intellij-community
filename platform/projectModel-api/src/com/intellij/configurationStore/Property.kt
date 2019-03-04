// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class Property(
  val description: String = "",
  val ignore: Boolean = false,

  /**
   * @return The name used in external formats, for example, JSON, .editorconfig.
   */
  val externalName: String = ""
)