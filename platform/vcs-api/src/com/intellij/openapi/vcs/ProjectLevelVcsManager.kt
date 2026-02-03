// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation
import com.intellij.openapi.vcs.VcsConfiguration.StandardOption
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener
import com.intellij.openapi.vcs.history.VcsHistoryCache
import com.intellij.openapi.vcs.impl.ContentRevisionCache
import com.intellij.openapi.vcs.impl.VcsDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

/**
 * Manages the version control systems used by a specific project.
 */
abstract class ProjectLevelVcsManager {
  /**
   * @return the list of all registered version control systems.
   */
  abstract fun getAllVcss(): Array<VcsDescriptor>

  /**
   * Returns the version control system with the specified name.
   *
   * @return the VCS instance, or `null` if none was found.
   */
  abstract fun findVcsByName(@NonNls name: @NonNls String?): AbstractVcs?

  /**
   * @return the list of VCSes supported by plugins.
   */
  abstract fun getAllSupportedVcss(): Array<AbstractVcs>

  /**
   * @return true if there are any VCSes used by at least one module in the project.
   */
  abstract fun hasActiveVcss(): Boolean

  /**
   * @return the list of VCSes used by at least one module in the project.
   */
  abstract fun getAllActiveVcss(): Array<AbstractVcs>

  /**
   * @return active VCS configured for the project, if there's only a single one. Return 'null' otherwise.
   */
  abstract fun getSingleVCS(): AbstractVcs?

  /**
   * Checks if the specified VCS is used by any of the modules in the project.
   */
  abstract fun checkVcsIsActive(vcs: AbstractVcs): Boolean

  /**
   * Checks if the VCS with the specified name is used by any of the modules in the project.
   */
  abstract fun checkVcsIsActive(@NonNls vcsName: @NonNls String?): Boolean

  /**
   * Checks if all given files are managed by the specified VCS.
   */
  abstract fun checkAllFilesAreUnder(abstractVcs: AbstractVcs, files: Array<VirtualFile>): Boolean

  abstract fun getConsolidatedVcsName(): @Nls String

  @NlsSafe
  abstract fun getShortNameForVcsRoot(file: VirtualFile): @NlsSafe String

  /**
   * Returns the VCS managing the specified file.
   *
   * @return the VCS instance, or `null` if the file does not belong to any module or the module
   * it belongs to is not under version control.
   */
  abstract fun getVcsFor(file: VirtualFile?): AbstractVcs?

  /**
   * Returns the VCS managing the specified file path.
   *
   * @return the VCS instance, or `null` if the file does not belong to any module or the module
   * it belongs to is not under version control.
   */
  abstract fun getVcsFor(file: FilePath?): AbstractVcs?

  /**
   * Return the parent directory of the specified file which is mapped to a VCS.
   *
   * @return the root, or `null` if the specified file is not in a VCS-managed directory.
   */
  abstract fun getVcsRootFor(file: VirtualFile?): VirtualFile?

  /**
   * Return the parent directory of the specified file path which is mapped to a VCS.
   *
   * @return the root, or `null` if the specified file is not in a VCS-managed directory.
   */
  abstract fun getVcsRootFor(file: FilePath?): VirtualFile?

  abstract fun getVcsRootObjectFor(file: VirtualFile?): VcsRoot?

  abstract fun getVcsRootObjectFor(file: FilePath?): VcsRoot?

  abstract fun hasAnyMappings(): Boolean

  /**
   * Whether vcs mappings were already processed after opening the project.
   * ie: if true, one can assume that [.hasActiveVcss] and [.hasAnyMappings] match if the mappings are correct.
   *
   * See [com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.VCS_ACTIVATED] listener that will be notified when this value changes.
   */
  open fun areVcsesActivated(): Boolean {
    return false
  }

  abstract fun getRootsUnderVcsWithoutFiltering(vcs: AbstractVcs): List<VirtualFile>

  abstract fun getRootsUnderVcs(vcs: AbstractVcs): Array<VirtualFile>

  abstract fun getAllVersionedRoots(): Array<VirtualFile>

  abstract fun getAllVcsRoots(): Array<VcsRoot>

  abstract fun setDirectoryMappings(mappings: List<VcsDirectoryMapping>)

  @Deprecated("Use just #setDirectoryMappings(List).")
  fun updateActiveVcss(): Unit = Unit

  abstract fun getDirectoryMappings(): List<VcsDirectoryMapping>

  abstract fun getDirectoryMappings(vcs: AbstractVcs): List<VcsDirectoryMapping>

  abstract fun getDirectoryMappingFor(path: FilePath): VcsDirectoryMapping?

  /**
   * This method can be used only when initially loading the project configuration!
   */
  @TestOnly
  abstract fun setDirectoryMapping(@NonNls path: @NonNls String, @NonNls activeVcsName: @NonNls String?)

  abstract fun iterateVcsRoot(root: VirtualFile, iterator: Processor<in FilePath>)

  abstract fun iterateVcsRoot(root: VirtualFile, iterator: Processor<in FilePath>, directoryFilter: VirtualFileFilter?)

  abstract fun iterateVfUnderVcsRoot(file: VirtualFile, processor: Processor<in VirtualFile>)

  abstract fun findVersioningVcs(file: VirtualFile): AbstractVcs?

  abstract fun getRootChecker(vcs: AbstractVcs): VcsRootChecker

  abstract val compositeCheckoutListener: CheckoutProvider.Listener

  abstract val vcsHistoryCache: VcsHistoryCache

  abstract val contentRevisionCache: ContentRevisionCache

  abstract val annotationLocalChangesListener: VcsAnnotationLocalChangesListener

  abstract fun isFileInContent(vf: VirtualFile?): Boolean

  abstract fun isIgnored(vf: VirtualFile): Boolean

  abstract fun isIgnored(filePath: FilePath): Boolean

  /**
   * Checks if a background VCS operation (commit or update) is currently in progress.
   */
  abstract val isBackgroundVcsOperationRunning: Boolean

  /**
   * Marks the beginning of a background VCS operation (commit or update).
   */
  abstract fun startBackgroundVcsOperation()

  /**
   * Marks the end of a background VCS operation (commit or update).
   */
  abstract fun stopBackgroundVcsOperation()

  /**
   * @see com.intellij.vcs.console.VcsConsoleTabService
   */
  abstract fun addMessageToConsoleWindow(@Nls message: @Nls String?, contentType: ConsoleViewContentType)

  /**
   * @see com.intellij.vcs.console.VcsConsoleTabService
   */
  abstract fun addMessageToConsoleWindow(line: VcsConsoleLine?)

  @RequiresEdt
  @Deprecated("Use {@link com.intellij.vcs.console.VcsConsoleTabService}")
  abstract fun showConsole(then: Runnable?)

  @RequiresEdt
  @Deprecated("Use {@link com.intellij.vcs.console.VcsConsoleTabService}")
  abstract fun scrollConsoleToTheEnd()

  @Deprecated("use {@link #addMessageToConsoleWindow(String, ConsoleViewContentType)}")
  abstract fun addMessageToConsoleWindow(@Nls message: @Nls String?, attributes: TextAttributes?)

  abstract fun getStandardOption(option: StandardOption, vcs: AbstractVcs): VcsShowSettingOption

  abstract fun getStandardConfirmation(option: StandardConfirmation, vcs: AbstractVcs?): VcsShowConfirmationOption

  /**
   * Execute the task on pooled thread, delayed until core vcs services are initialized.
   */
  abstract fun runAfterInitialization(runnable: Runnable)

  abstract suspend fun awaitInitialization()

  companion object {
    /**
     * Fired when [.getVcsFor] and similar methods change their value.
     */
    @JvmField
    @Topic.ProjectLevel
    val VCS_CONFIGURATION_CHANGED: Topic<VcsMappingListener> =
      Topic(VcsMappingListener::class.java, Topic.BroadcastDirection.NONE)

    /**
     * This event is only fired by SVN plugin.
     *
     * Typically, it can be ignored unless plugin supports SVN and uses
     * [.getRootsUnderVcs], [.getAllVcsRoots] or [.getAllVersionedRoots] methods.
     *
     * See [org.jetbrains.idea.svn.SvnFileUrlMappingImpl] and [AbstractVcs.getCustomConvertor].
     */
    @JvmField
    @Topic.ProjectLevel
    val VCS_CONFIGURATION_CHANGED_IN_PLUGIN: Topic<PluginVcsMappingListener> =
      Topic(PluginVcsMappingListener::class.java, Topic.BroadcastDirection.NONE)

    /**
     * Returns the instance for the specified project.
     */
    @JvmStatic
    fun getInstance(project: Project): ProjectLevelVcsManager = project.service()
  }

  // region Kotlin compatibility
  // MANUALLY CHECK THE EXTERNAL USAGES BEFORE REMOVING !!!

  // used by Junie plugin
  @Suppress("unused")
  @Deprecated("Use #getAllVcss instead", ReplaceWith("getAllVcss()"))
  val allVcss: Array<VcsDescriptor>
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getAllVcssDoNotUse")
    @JvmSynthetic
    get() = getAllVcss()

  // used by Google AI plugin
  @Suppress("unused")
  @Deprecated("Use #getAllSupportedVcss instead", ReplaceWith("getAllSupportedVcss()"))
  val allSupportedVcss: Array<AbstractVcs>
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getAllSupportedVcssDoNotUse")
    @JvmSynthetic
    get() = getAllSupportedVcss()

  @Suppress("unused")
  @Deprecated("Use #getAllActiveVcss instead", ReplaceWith("getAllActiveVcss()"))
  val allActiveVcss: Array<AbstractVcs>
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getAllActiveVcssDoNotUse")
    @JvmSynthetic
    get() = getAllActiveVcss()

  @Suppress("unused")
  @Deprecated("Use #getSingleVCS instead", ReplaceWith("getSingleVCS()"))
  val singleVCS: AbstractVcs?
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getSingleVcssDoNotUse")
    @JvmSynthetic
    get() = getSingleVCS()

  @Suppress("unused")
  @Deprecated("Use #getAllVersionedRoots instead", ReplaceWith("getAllVersionedRoots()"))
  val allVersionedRoots: Array<VirtualFile>
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getAllVersionedRootsDoNotUse")
    @JvmSynthetic
    get() = getAllVersionedRoots()

  @Suppress("unused")
  @Deprecated("Use #getAllVcsRoots instead", ReplaceWith("getAllVcsRoots()"))
  val allVcsRoots: Array<VcsRoot>
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getAllVcsRootsDoNotUse")
    @JvmSynthetic
    get() = getAllVcsRoots()

  @Suppress("unused")
  var directoryMappings: List<VcsDirectoryMapping>
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getDirectoryMappingsDoNotUse")
    @JvmSynthetic
    @Deprecated("Use #getDirectoryMappings instead", ReplaceWith("getDirectoryMappings()"))
    get() = getDirectoryMappings()
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("setDirectoryMappingsDoNotUse")
    @JvmSynthetic
    @Deprecated("Use #setDirectoryMappings instead", ReplaceWith("setDirectoryMappings(value)"))
    set(value) {
      setDirectoryMappings(value)
    }
  // endregion
}
