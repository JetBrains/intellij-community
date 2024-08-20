// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys.SINGLETON_EDITOR_IN_WINDOW
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

object FileEditorManagerKeys {
  /**
   * Works on [FileEditor] objects
   */
  @JvmField
  @ApiStatus.Internal
  val DUMB_AWARE: Key<Boolean> = Key.create("DUMB_AWARE")

  /**
   * Works on [Project] objects
   */
  @JvmField
  @TestOnly
  @ApiStatus.Internal
  val ALLOW_IN_LIGHT_PROJECT: Key<Boolean> = Key.create("ALLOW_IN_LIGHT_PROJECT")

  /**
   * Flag is temporarily set if the editor is being closed to be reopened again (in another split, for example).
   *
   * Works on [VirtualFile] objects
   */
  @JvmField
  val CLOSING_TO_REOPEN: Key<Boolean> = Key.create("CLOSING_TO_REOPEN")

  /**
   * Disables the Preview Tab functionality for marked files.
   * If a virtual file has this key is set to TRUE, the corresponding editor will always be opened in a regular tab.
   *
   * Works on [VirtualFile] objects
   */
  @JvmField
  val FORBID_PREVIEW_TAB: Key<Boolean> = Key.create("FORBID_PREVIEW_TAB")

  /**
   * If the marked [JComponent] is the parent of a currently focused component, the [openFile] requests will try to open in a preview tab.
   *
   * See also [com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions.usePreviewTab].
   * Works on [JComponent] objects
   */
  @JvmField
  val OPEN_IN_PREVIEW_TAB: Key<Boolean> = Key.create("OPEN_IN_PREVIEW_TAB")

  /**
   * Prohibits showing the [VirtualFile] in two [FileEditor] simultaneously.
   * The new [openFile] requests will close old editors before opening a new one.
   *
   * Works on [VirtualFile] objects
   */
  @JvmField
  val FORBID_TAB_SPLIT: Key<Boolean> = Key.create("FORBID_TAB_SPLIT")

  /**
   * Forces opening new editor tabs in other editor splits.
   *
   * If the marked editor is shown in DockWindow, the window will not be used to show other file editors.
   * The other file editors will be opened in the main window splitters instead.
   * It will also suppress showing the editor tabs in this DockWindow.
   *
   * If the marked editor is shown in a Split, a sibling split will be used instead.
   *
   * See also [com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions.isSingletonEditorInWindow].
   * Works on [FileEditor] objects
   */
  @JvmField
  @ApiStatus.Internal
  val SINGLETON_EDITOR_IN_WINDOW: Key<Boolean> = Key.create("OPEN_OTHER_TABS_IN_MAIN_WINDOW")

  /**
   * Disables NavBar in DockWindow that contains marked [FileEditor].
   * Typically, should be used in combination with [SINGLETON_EDITOR_IN_WINDOW].
   *
   * Works on [FileEditor] objects
   */
  @JvmField
  @ApiStatus.Internal
  val SHOW_NORTH_PANEL: Key<Boolean> = Key.create("SHOW_NORTH_PANEL")

  /**
   * Allows remembering window position when the file is opened in DockWindow.
   * Typically, should be used in combination with [SINGLETON_EDITOR_IN_WINDOW].
   *
   * Works on [VirtualFile] objects
   */
  @JvmField
  @ApiStatus.Internal
  val WINDOW_DIMENSION_KEY: Key<String> = Key.create("WINDOW_DIMENSION_KEY")

  /**
   * If set to `false`, disables restoring the DockWindow on project opening, if they were opened on project closing.
   * Typically, should be used in combination with [SINGLETON_EDITOR_IN_WINDOW].
   *
   * Works on [VirtualFile] objects
   */
  @JvmField
  @ApiStatus.Internal
  val REOPEN_WINDOW: Key<Boolean> = Key.create("REOPEN_WINDOW")
}