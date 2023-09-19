// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.impl.ComponentInlayImpl
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component

@Experimental
interface ComponentInlay<T : Component> {
  val component: T
  val inlay: Inlay<*>
  val disposable get() = inlay
}

@Experimental
fun <T : Component> Editor.addComponentInlay(offset: Int, properties: InlayProperties, component: T, alignment: ComponentInlayAlignment? = null, customRenderer: CustomComponentInlayRenderer? = null): ComponentInlay<T>? =
  ComponentInlayImpl.add(this, offset, properties, component, alignment, customRenderer)