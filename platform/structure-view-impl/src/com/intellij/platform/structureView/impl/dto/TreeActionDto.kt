// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl.dto

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.openapi.util.NlsActions
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
@Serializable
data class TreeActionPresentationDto(
  val icon: IconId?,
  val text: @Nls String,
  val description: @Nls String?,
)

fun ActionPresentation.toDto(): TreeActionPresentationDto {
  return TreeActionPresentationDto(icon.rpcId(), text, description)
}

fun TreeActionPresentationDto.toPresentation(): ActionPresentation {
  return object : ActionPresentation {
    override fun getText(): @NlsActions.ActionText String = this@toPresentation.text
    override fun getDescription(): @NlsActions.ActionDescription String? = this@toPresentation.description
    override fun getIcon(): Icon? = this@toPresentation.icon?.icon()
  }
}
