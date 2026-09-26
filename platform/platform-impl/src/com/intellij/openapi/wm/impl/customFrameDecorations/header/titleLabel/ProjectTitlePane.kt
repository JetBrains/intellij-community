// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ui.UIUtil
import java.awt.FontMetrics
import javax.swing.JComponent

internal class ProjectTitlePane : ShrinkingTitlePart {
  private val openChat = " ["
  private val closeChar = "]"

  private val unparsed = DefaultPartTitle()
  private val projectTitle = ProjectTitle()

  override var active: Boolean
    get() = projectTitle.active
    set(value) {
      projectTitle.active = value
    }

  var project: Project? = null
    set(value) {
      field = value
      updatePath()
    }

  private fun updatePath() {
    project?.let {
      if (it.isDisposed) {
        return@let
      }

      val name = it.name
      val path = FileUtil.toSystemDependentName(FileUtil.getLocationRelativeToUserHome(it.basePath))

      projectTitle.project = name
      projectTitle.path = path

      unparsed.shortText = name
      unparsed.longText = "$name$openChat$path$closeChar"

      projectTitle.openChar = openChat
      projectTitle.closeChar = closeChar
      return
    }

    projectTitle.project = ""
    projectTitle.path = ""

    unparsed.shortText = ""
    unparsed.longText = ""
  }

  override val longWidth: Int
    get() = projectTitle.longWidth
  override val shortWidth: Int
    get() = projectTitle.shortWidth
  override val toolTipPart: String
    get() = unparsed.toolTipPart

  override fun getLong(): String {
    return projectTitle.getLong()
  }

  override fun getShort(): String {
    return projectTitle.getShort()
  }

  override fun refresh(label: JComponent, fm: FontMetrics) {
    unparsed.refresh(label, fm)
    projectTitle.refresh(label, fm)
  }

  override fun shrink(label: JComponent, fm: FontMetrics, maxWidth: Int): String {
    return projectTitle.shrink(label, fm, maxWidth)
  }
}

class ProjectTitle : ShrinkingTitlePart {

  private var text: String = ""
  private val description = ClippingTitle()

  private var projectTextWidth: Int = 0
  private var longTextWidth: Int = 0

  override var active: Boolean
    get() = description.active
    set(value) {
      description.active = value
    }

  var openChar: String
    get() = description.prefix
    set(value) {
      description.prefix = value
    }

  var closeChar: String
    get() = description.suffix
    set(value) {
      description.suffix = value
    }

  var path: String
    get() = description.longText
    set(value) {
      description.longText = value
    }

  var project: String = ""

  override val longWidth: Int
    get() = longTextWidth
  override val shortWidth: Int
    get() = projectTextWidth
  override val toolTipPart: String
    get() = project + description.getLong()

  override fun getLong(): String {
    text = project + description.getLong()
    return text
  }

  override fun getShort(): String {
    text = project
    return text
  }

  override fun shrink(label: JComponent, fm: FontMetrics, maxWidth: Int): String {
    return when {
      maxWidth > longWidth -> {
        text = project + description.getLong()
        text
      }
      maxWidth > shortWidth + description.shortWidth -> {
        text = project + description.shrink(label, fm, maxWidth - shortWidth)
        text
      }
      else -> {
        text = project
        text
      }
    }
  }

  override fun refresh(label: JComponent, fm: FontMetrics) {
    description.refresh(label, fm)

    projectTextWidth = if (project.isEmpty()) 0 else UIUtil.computeStringWidth(label, fm, project)
    longTextWidth = projectTextWidth + description.longWidth
  }
}