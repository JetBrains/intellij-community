// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.ex.FrameProjectsConfigurableGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.IdeFrameGuests
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Consumer

/** The question about the pages of a frame guest that closes while a Settings window is open. */
internal object SettingsFrameGuestClose {
  /**
   * Asks about the changes of the pages of [project] in [editor] before [project] closes, see [FrameProjectsConfigurableGroup].
   * [ask] shows the question with the given message and gives [Messages.YES] to apply, [Messages.NO] to drop the changes,
   * or [Messages.CANCEL] to keep the project open.
   *
   * Returns `true` when [project] can close: it is not a project of the selector of [editor], it has no change, or the user
   * applied or dropped the changes. Returns `false` when the user cancels or when a page rejects its values.
   * The pages of [project] leave the tree when the project closes, see [SettingsEditor].
   */
  @RequiresEdt
  fun canClose(editor: SettingsEditor, project: Project, ask: (String) -> Int): Boolean {
    val group = editor.frameProjectsGroup ?: return true
    if (project !in group.projects.projects) {
      return true
    }
    // the editor checks a page for changes only after a delay, and a change of the last second must count too
    editor.checkCurrentModified()
    if (!group.isModified(project)) {
      return true
    }
    return when (ask(ApplicationBundle.message("settings.close.project.unsaved.message", project.name))) {
      Messages.YES -> editor.applyFrameProject(project)
      Messages.NO -> {
        editor.cancelFrameProject(project)
        true
      }
      else -> false
    }
  }
}

/**
 * Adds a frame guest that opens while a Settings window is open to the tree of that window: a tab and the pages of the guest.
 * The Settings editor registers its tree with [follow]. [SettingsFrameGuestActivity] reports a guest that finished its open.
 */
internal object SettingsFrameGuestJoins {
  private class Follower(
    val group: FrameProjectsConfigurableGroup,
    val coroutineScope: CoroutineScope,
    val joined: Consumer<Boolean>,
  )

  private val followers = DisposableWrapperList<Follower>()

  /**
   * Adds each frame guest of the frame owner of [group] that opens later to [group], until [parentDisposable] is disposed.
   * [joined] runs on the EDT after a join. It gets `true` when the tree got a new node, see [FrameProjectsConfigurableGroup.join].
   */
  @JvmStatic
  fun follow(group: FrameProjectsConfigurableGroup, coroutineScope: CoroutineScope, parentDisposable: Disposable, joined: Consumer<Boolean>) {
    followers.add(Follower(group, coroutineScope, joined), parentDisposable)
  }

  /** Adds [project], an open frame guest, to each tree of its frame owner. */
  fun guestOpened(project: Project) {
    for (follower in followers) {
      if (isJoining(follower.group, project)) {
        follower.coroutineScope.launch {
          join(follower.group, project, follower.joined)
        }
      }
    }
  }

  /** Builds the tree of [project] in the background, and adds it to [group] on the EDT. */
  @VisibleForTesting
  suspend fun join(group: FrameProjectsConfigurableGroup, project: Project, joined: Consumer<Boolean>) {
    val memberTree = withContext(Dispatchers.Default) {
      if (project.isDisposed) null else FrameProjectsConfigurableGroup.buildMemberTree(project)
    } ?: return
    withContext(Dispatchers.EDT) {
      // the guest can close, and the window can rebuild its tree, while the tree of the guest builds
      if (!isJoining(group, project) || project !in SettingsFrameProjects.listProjects(group.projects.frameOwner)) {
        return@withContext
      }
      val hasNewNodes = group.join(project, memberTree)
      group.projects.add(project)
      joined.accept(hasNewNodes)
    }
  }

  private fun isJoining(group: FrameProjectsConfigurableGroup, project: Project): Boolean {
    val frameOwner = group.projects.frameOwner
    return !project.isDisposed && !frameOwner.isDisposed && project !== frameOwner &&
           IdeFrameGuests.frameOwnerOf(project) === frameOwner && project !in group.projects.projects && group.canJoin()
  }
}

/** Reports a frame guest that finished its open to [SettingsFrameGuestJoins]. Then the guest is initialized. */
internal class SettingsFrameGuestActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (IdeFrameGuests.isFrameGuest(project)) {
      SettingsFrameGuestJoins.guestOpened(project)
    }
  }
}
