// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.psi.ComposeResourcesPsiChangesListener
import com.intellij.compose.ide.plugin.resources.psi.generateAccessorsFrom
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.milliseconds

/**
 * Service responsible for handling the generation of Compose resource-related Gradle tasks.
 * This service listens to virtual file system (VFS) changes and triggers tasks to generate necessary
 * Compose resources files for corresponding modules and source sets.
 *
 */
@Service(Service.Level.PROJECT)
internal class ComposeResourcesGenerationService(private val project: Project, private val scope: CoroutineScope) : Disposable {
  private val notificationService = ComposeResourcesNotificationService.getInstance(project)

  private val composeResourcesPsiChangesFlow = MutableSharedFlow<ComposeResourcesDir>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @TestOnly
  val composeResourcesPsiChanges = composeResourcesPsiChangesFlow.asSharedFlow() // only used to test psi changes listener

  fun tryEmit(composeResourcesDir: ComposeResourcesDir) = composeResourcesPsiChangesFlow.tryEmit(composeResourcesDir)

  /**
   * Handles PSI changes related to Compose resources by collecting and processing them in chunks.
   */
  private suspend fun handleComposeResourcesPsiChanges() =
    composeResourcesPsiChangesFlow
      .debounceBatch(950.milliseconds)
      .collectLatest { eventsChunk ->
        val changedComposeResourcesDirs = eventsChunk.toSet()
        try {
          project.generateAccessorsFrom(changedComposeResourcesDirs)
        }
        catch (e: IllegalStateException) {
          notificationService.notifyError(
            ComposeIdeBundle.message("compose.resources.notification.title"), e.message ?: "Unknown error. Please rebuild the project.",
          )
        }
      }

  companion object {
    fun getInstance(project: Project): ComposeResourcesGenerationService = project.service()
  }

  /** Handle PSI changes related to Compose resource changes */
  internal class ComposeResourcesWatcherActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      // in unit test mode, activities can block project opening and cause timeouts
      if (ApplicationManager.getApplication().isUnitTestMode) return
      val composeResourcesGenerationService = project.service<ComposeResourcesGenerationService>()
      PsiManager.getInstance(project).addPsiTreeChangeListener(ComposeResourcesPsiChangesListener(project), composeResourcesGenerationService)
      composeResourcesGenerationService.handleComposeResourcesPsiChanges()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun dispose() {
    composeResourcesPsiChangesFlow.resetReplayCache()
  }
}