/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.module.impl

import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag

@Tag("module-renaming")
class ModuleRenamingHistoryState {
  @Property(surroundWithTag = false)
  @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, entryTagName = "module",
                 keyAttributeName = "old-name", valueAttributeName = "new-name")
  @JvmField
  var oldToNewName = LinkedHashMap<String, String>()
}