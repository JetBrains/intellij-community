// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import sun.swing.SwingUtilities2
import java.awt.FontMetrics
import java.io.File
import javax.swing.JComponent


class ProjectTitlePane : ShrinkingTitlePart {
  private val unparsed = DefaultPartTitle()
  private val projectTitle = ProjectTitle()

  var parsed = false
  override var active: Boolean
    get() = projectTitle.active
    set(value) {
      projectTitle.active = value
    }

  fun setProject(lng: String, short: String) {
    val long = if (lng.length > short.length) lng else short

    unparsed.longText = long
    unparsed.shortText = short

    val regex = """(.*)(\[)(.*)(])(.*)""".toRegex()
    val regex1 = """(.*)(\()(.*)(\))(.*)""".toRegex()
    val regex2 = """(.*)(<)(.*)(>)(.*)""".toRegex()
    val regex3 = """(.*)(\{)(.*)(})(.*)""".toRegex()
    val match = regex.matchEntire(long)
                ?: regex1.matchEntire(long)
                ?: regex2.matchEntire(long)
                ?: regex3.matchEntire(long)

    parsed = match?.let {
      val (before, open, path, close, after) = match.destructured

      val project = before.trim()
      if (project == short && path.isNotEmpty() && File(path).exists()) {
        projectTitle.project = project
        projectTitle.openChar = " $open"
        projectTitle.closeChar = close
        projectTitle.path = path

        true
      }
      else false
    } ?: false
  }

  override val longWidth: Int
    get() = if (parsed) projectTitle.longWidth else unparsed.longWidth
  override val shortWidth: Int
    get() = if (parsed) projectTitle.shortWidth else unparsed.shortWidth
  override val toolTipPart: String
    get() = unparsed.toolTipPart
  override fun getLong(): String {
    return if(parsed) projectTitle.getLong() else unparsed.getLong()
  }

  override fun getShort(): String {
    return if(parsed) projectTitle.getShort() else unparsed.getShort()
  }

  override fun refresh(label: JComponent, fm: FontMetrics) {
    unparsed.refresh(label, fm)
    projectTitle.refresh(label, fm)
  }

  override fun shrink(label: JComponent, fm: FontMetrics, maxWidth: Int): String {
    return when {
      parsed -> {
        projectTitle.shrink(label, fm, maxWidth)
      }
      else -> {
        return if (maxWidth > unparsed.longWidth) {
          unparsed.getLong()
        }
        else {
          unparsed.getShort()
        }
      }
    }
  }
}

class ProjectTitle : ShrinkingTitlePart {

  private var text: String =""
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
    get() = project+description.getLong()

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

    projectTextWidth = if (project.isEmpty()) 0 else SwingUtilities2.stringWidth(label, fm, project)
    longTextWidth = projectTextWidth + description.longWidth
  }
}