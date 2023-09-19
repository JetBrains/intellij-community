// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.impl.ComponentInlayManager
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component

@Experimental
fun <T : Component> Editor.addComponentInlay(offset: Int,
                                             properties: InlayProperties,
                                             component: T,
                                             alignment: ComponentInlayAlignment? = null): Inlay<ComponentInlayRenderer<T>>? =
  addComponentInlay(offset, properties, ComponentInlayRenderer(component, alignment))

@Experimental
fun <T : Component> Editor.addComponentInlay(offset: Int,
                                             properties: InlayProperties,
                                             renderer: ComponentInlayRenderer<T>): Inlay<ComponentInlayRenderer<T>>? =
  ComponentInlayManager.add(this, offset, properties, renderer)