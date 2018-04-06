// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

enum class PropertyKind(private val prefix: String) {

  GETTER("get"),
  BOOLEAN_GETTER("is"),
  SETTER("set");

  /**
   * @return accessor name by [propertyName] assuming it is valid
   */
  fun getAccessorName(propertyName: String): String {
    val suffix = if (propertyName.length > 1 && propertyName[1].isUpperCase()) {
      propertyName
    }
    else {
      propertyName.capitalize()
    }
    return prefix + suffix
  }
}
