// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.util.Pair
import org.jdom.Element
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

class LibraryStateSplitter : StateSplitterEx() {
  override fun splitState(state: Element): MutableList<Pair<Element, String>> = splitState(
    state, JpsLibraryTableSerializer.NAME_ATTRIBUTE)
}