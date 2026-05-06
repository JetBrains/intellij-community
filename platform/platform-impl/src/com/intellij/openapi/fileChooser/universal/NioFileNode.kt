// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.TreeVisitor.Action
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

/**
 * A tree node backed by a [Path].
 */
@ApiStatus.Internal
open class NioFileNode internal constructor(val path: Path?) {
  private val iconRef = AtomicReference<Icon?>()
  private val nameRef = AtomicReference<@NlsSafe String?>()
  private val commentRef = AtomicReference<@NlsSafe String?>()
  private val validRef = AtomicBoolean()
  private val hiddenRef = AtomicBoolean()
  private val specialRef = AtomicBoolean()
  private val symlinkRef = AtomicBoolean()
  private val writableRef = AtomicBoolean()

  val icon: Icon? get() = iconRef.get()

  internal fun updateIcon(icon: Icon?): Boolean = icon != iconRef.getAndSet(icon)

  val name: @NlsSafe String? get() = nameRef.get()

  internal fun updateName(name: String?): Boolean = name != nameRef.getAndSet(name)

  val comment: @NlsSafe String? get() = commentRef.get()

  internal fun updateComment(comment: String?): Boolean = comment != commentRef.getAndSet(comment)

  val isValid: Boolean get() = validRef.get()

  internal fun updateValid(valid: Boolean): Boolean = valid != validRef.getAndSet(valid)

  val isHidden: Boolean get() = hiddenRef.get()

  internal fun updateHidden(hidden: Boolean): Boolean = hidden != hiddenRef.getAndSet(hidden)

  val isSpecial: Boolean get() = specialRef.get()

  internal fun updateSpecial(special: Boolean): Boolean = special != specialRef.getAndSet(special)

  val isSymlink: Boolean get() = symlinkRef.get()

  internal fun updateSymlink(symlink: Boolean): Boolean = symlink != symlinkRef.getAndSet(symlink)

  val isWritable: Boolean get() = writableRef.get()

  internal fun updateWritable(writable: Boolean): Boolean = writable != writableRef.getAndSet(writable)

  internal class Visitor(path: Path) : TreeVisitor.ByComponent<Path, Path>(path, { (it as? NioFileNode)?.path }) {
    override fun visit(path: Path?): Action = if (path == null) Action.CONTINUE else super.visit(path)

    override fun contains(pathFile: Path, thisFile: Path): Boolean = thisFile.startsWith(pathFile) && thisFile != pathFile
  }
}
