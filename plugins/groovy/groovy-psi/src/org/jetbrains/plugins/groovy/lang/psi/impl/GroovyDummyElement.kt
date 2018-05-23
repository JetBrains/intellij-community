// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.tree.IElementType

class GroovyDummyElement(val childType: IElementType, text: CharSequence?) : FileElement(GroovyDummyElementType, text)
