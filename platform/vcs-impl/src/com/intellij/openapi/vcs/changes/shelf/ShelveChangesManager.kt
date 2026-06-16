// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.google.common.collect.Lists
import com.intellij.configurationStore.serialize
import com.intellij.history.ActivityId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus
import com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.PatchEP
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.NonLazySchemeProcessor
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.notNullize
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotificationIdsHolder
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.getChangeListNameForUnshelve
import com.intellij.openapi.vcs.changes.getPredefinedChangeList
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor
import com.intellij.openapi.vcs.changes.patch.PatchFileType
import com.intellij.openapi.vcs.changes.patch.PatchNameChecker
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog
import com.intellij.openapi.vcs.changes.ui.RollbackWorker
import com.intellij.openapi.vcs.changes.ui.ShelvedChangeListDragBean
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.Shelve
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.ArrayUtil
import com.intellij.util.ModalityUiUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.messages.Topic
import com.intellij.util.text.CharArrayCharSequence
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.vcs.VcsActivity
import com.intellij.vcsUtil.FilesProgress
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import org.jdom.Element
import org.jdom.Parent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Calendar
import java.util.Date
import java.util.LinkedList
import java.util.Collections
import java.util.Objects
import java.util.TreeSet
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import kotlin.time.Duration.Companion.days

@Service(Service.Level.PROJECT)
@State(name = "ShelveChangesManager", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ShelveChangesManager(project: Project, coroutineScope: CoroutineScope) : PersistentStateComponent<Element> {
  private val myPathMacroSubstitutor: PathMacroManager
  private val SHELVED_FILES_LOCK: ReadWriteLock = ReentrantReadWriteLock(true)
  private val myTracer: Tracer = TelemetryManager.getInstance().getTracer(VcsScope)
  private val myProject: Project
  val coroutineScope: CoroutineScope
  private var myState: State = State()
  private var schemeManager: SchemeManager<ShelvedChangeList>
  private var myShelvingFiles: MutableSet<VirtualFile>? = null
  private val deferredInit: Deferred<Unit>

  init {
    myPathMacroSubstitutor = PathMacroManager.getInstance(project)
    myProject = project
    this.coroutineScope = coroutineScope
    val vcsConfiguration: VcsConfiguration = VcsConfiguration.getInstance(project)
    schemeManager =
      createShelveSchemeManager(
        project,
        if (vcsConfiguration.USE_CUSTOM_SHELF_PATH) vcsConfiguration.CUSTOM_SHELF_PATH else null
      )

    coroutineScope.launch(CoroutineName("ShelveChangesManager cleanup")) {
      delay(1.days)
      while (isActive) {
        try {
          cleanDeletedOlderOneWeek()
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          LOG.error("Couldn't clean deleted shelves", t)
        }
        delay(1.days)
      }
    }
    deferredInit = coroutineScope.async(start = CoroutineStart.DEFAULT) {
      try {
        schemeManager.loadSchemes()
        //workaround for ignoring not valid patches, because readScheme doesn't support nullable value as it should be
        filterNonValidShelvedChangeLists()
        markDeletedSystemUnshelved()
        cleanDeletedOlderOneWeek()
        notifyStateChanged()
      }
      catch (e: Exception) {
        LOG.error("Couldn't read shelf information", e)
      }
    }
  }

  override fun getState(): Element =
    //provide new element if all State fields have their default values  - > to delete existing settings in xml,
    ObjectUtils.chooseNotNull(serialize(myState), EMPTY_ELEMENT)

  public override fun loadState(state: Element) {
    myState = XmlSerializer.deserialize(state, State::class.java)
    try {
      migrateOldShelfInfo(state, false)
      migrateOldShelfInfo(state, true)
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }


  private fun createShelveSchemeManager(
    project: Project,
    customPath: String?,
  ): SchemeManager<ShelvedChangeList> {
    val customShelfFilePath: FilePath? =
      if (customPath != null) VcsUtil.getFilePath(myPathMacroSubstitutor.expandPath(customPath)!!) else null
    //don't collapse custom paths
    val shouldCollapsePath: Boolean = !VcsConfiguration.getInstance(myProject).USE_CUSTOM_SHELF_PATH
    return SchemeManagerFactory.getInstance(project)
      .create(
        if (customShelfFilePath != null) customShelfFilePath.getName() else SHELVE_MANAGER_DIR_PATH,
        object : NonLazySchemeProcessor<ShelvedChangeList, ShelvedChangeList>() {
          @Throws(InvalidDataException::class)
          public override fun readScheme(element: Element, duringLoad: Boolean): ShelvedChangeList {
            return readOneShelvedChangeList(element)
          }

          @Throws(WriteExternalException::class)
          public override fun writeScheme(scheme: ShelvedChangeList): Parent {
            val child: Element = Element(ELEMENT_CHANGELIST)
            ShelvedChangeList.writeExternal(
              scheme,
              child,
              if (shouldCollapsePath) myPathMacroSubstitutor else null
            )
            return child
          }
        }, null, directoryPath = if (customPath != null) Paths.get(customPath) else null
      )
  }

  @ApiStatus.Internal
  @VisibleForTesting
  fun scheduleShelvesLoading(): Deferred<Unit?> {
    deferredInit.start()
    return deferredInit
  }

  private fun filterNonValidShelvedChangeLists() {
    val allSchemes: MutableList<ShelvedChangeList> = ArrayList(schemeManager.allSchemes)
    var i = 0
    val size = allSchemes.size
    while (i < size) {
      val t: ShelvedChangeList = allSchemes.get(i)
      if (!t.isValid()) {
        schemeManager.removeScheme(t)
      }
      i++
    }
  }

  fun checkAndMigrateUnderProgress(fromFile: File, toFile: File, wasCustom: Boolean) {
    val newSchemeManager: SchemeManager<ShelvedChangeList> =
      createShelveSchemeManager(myProject, VcsUtil.getFilePath(toFile).path)
    newSchemeManager.loadSchemes()
    if (VcsConfiguration.getInstance(myProject).MOVE_SHELVES && fromFile.exists()) {
      object : Task.Modal(myProject, VcsBundle.message("shelve.copying.shelves.to.progress"), true) {
        public override fun run(indicator: ProgressIndicator) {
          LOG.info(
            java.lang.String.format(
              "Migrating existing shelves. Old location: %s, new location: %s",
              schemeManager.allSchemes.size, newSchemeManager.allSchemes.size
            )
          )

          for (list in schemeManager.allSchemes) {
            if (!list.isValid()) continue
            try {
              val newTargetDirectory: Path =
                suggestPatchName(this@ShelveChangesManager.myProject, list.getDescription(), toFile, "").toPath()
              val migratedList: ShelvedChangeList =
                createChangelistCopyWithChanges(list, newTargetDirectory)
              newSchemeManager.addScheme(migratedList, false)
              indicator.checkCanceled()
            }
            catch (e: IOException) {
              LOG.error("Can't copy patch file: " + list.getPath())
            }
          }

          LOG.info(
            java.lang.String.format(
              "Migrating existing shelves finished. Old location: %s, new location: %s",
              schemeManager.allSchemes.size, newSchemeManager.allSchemes.size
            )
          )

          clearShelvedLists(schemeManager.allSchemes.toMutableList(), false)
          LOG.info("Cleaned old shelve location")
        }

        public override fun onSuccess() {
          super.onSuccess()
          updateShelveSchemaManager(newSchemeManager)
        }

        public override fun onCancel() {
          super.onCancel()
          suggestToCancelMigrationOrRevertPathToPrevious()
        }

        //
        fun suggestToCancelMigrationOrRevertPathToPrevious() {
          if (Messages.showOkCancelDialog(
              myProject,
              VcsBundle.message("shelve.moving.failed.prompt"),
              VcsBundle.message("shelve.error.title"),
              VcsBundle.message("shelve.use.new.directory.button"),
              VcsBundle.message("shelve.revert.moving.button"),
              UIUtil.getWarningIcon()
            ) === Messages.OK
          ) {
            updateShelveSchemaManager(newSchemeManager)
          }
          else {
            val vcsConfiguration: VcsConfiguration = VcsConfiguration.getInstance(this@ShelveChangesManager.myProject)
            vcsConfiguration.USE_CUSTOM_SHELF_PATH = wasCustom
            if (wasCustom) {
              vcsConfiguration.CUSTOM_SHELF_PATH = FileUtil.toSystemIndependentName(fromFile.getPath())
            }
          }
        }

        public override fun onThrowable(error: Throwable) {
          super.onThrowable(error)
          suggestToCancelMigrationOrRevertPathToPrevious()
        }
      }.queue()
    }
    else {
      updateShelveSchemaManager(newSchemeManager)
    }
  }

  private fun updateShelveSchemaManager(newSchemeManager: SchemeManager<ShelvedChangeList>) {
    myProject.save()
    ApplicationManager.getApplication().saveSettings()
    SchemeManagerFactory.getInstance(myProject).dispose(schemeManager)
    schemeManager = newSchemeManager
    notifyStateChanged()
  }

  val shelfResourcesDirectory: File
    get() = schemeManager.rootDirectory

  @Throws(InvalidDataException::class)
  private fun readOneShelvedChangeList(element: Element): ShelvedChangeList {
    return ShelvedChangeList.readExternal(element, myPathMacroSubstitutor)
  }

  //load old shelf information from workspace.xml without moving .patch and binary files into new directory
  @Throws(InvalidDataException::class, IOException::class)
  private fun migrateOldShelfInfo(element: Element, recycled: Boolean) {
    for (changeSetElement in element.getChildren(if (recycled) ELEMENT_RECYCLED_CHANGELIST else ELEMENT_CHANGELIST)) {
      val list: ShelvedChangeList = readOneShelvedChangeList(changeSetElement)
      if (!list.isValid()) {
        break
      }
      val uniqueDir: Path = generateUniqueSchemePatchDir(list.getDescription(), false)
      list.setName(uniqueDir.getFileName().toString())
      list.setRecycled(recycled)
      schemeManager.addScheme(list, false)
    }
  }

  val shelvedChangeLists: List<ShelvedChangeList>
    get() = getRecycled(false)

  private fun getRecycled(recycled: Boolean): @Unmodifiable List<ShelvedChangeList> {
    val collection: List<ShelvedChangeList> = schemeManager.allSchemes
    if (collection.isEmpty()) {
      return emptyList()
    }

    val result: MutableList<ShelvedChangeList> = ArrayList()
    for (t in collection) {
      if (recycled == t.isRecycled() && !t.isDeleted()) {
        result.add(t)
      }
    }
    return Collections.unmodifiableList(result)
  }

  val allLists: List<ShelvedChangeList>
    get() = schemeManager.allSchemes.toList()

  @Throws(IOException::class, VcsException::class)
  fun shelveChanges(
    changes: MutableCollection<out Change>,
    commitMessage: String,
    rollback: Boolean,
  ): ShelvedChangeList {
    return shelveChanges(changes, commitMessage, rollback, false)
  }

  @Throws(IOException::class, VcsException::class)
  fun shelveChanges(
    changes: MutableCollection<out Change>,
    commitMessage: String,
    rollback: Boolean,
    markToBeDeleted: Boolean,
  ): ShelvedChangeList {
    return shelveChanges(changes, commitMessage, rollback, markToBeDeleted, false)
  }

  @Throws(IOException::class, VcsException::class)
  fun shelveChanges(
    changes: MutableCollection<out Change>,
    commitMessage: String,
    rollback: Boolean,
    markToBeDeleted: Boolean,
    honorExcludedFromCommit: Boolean,
  ): ShelvedChangeList {
    val progressIndicator: ProgressIndicator? = ProgressManager.getInstance().getProgressIndicator()
    if (progressIndicator != null) {
      progressIndicator.setText(VcsBundle.message("shelve.changes.progress.text"))
    }
    val shelveList: ShelvedChangeList
    try {
      SHELVED_FILES_LOCK.writeLock().lock()
      rememberShelvingFiles(changes)
      shelveList = createShelfFromChanges(changes, commitMessage, markToBeDeleted, honorExcludedFromCommit)
    }
    finally {
      cleanShelvingFiles()
      SHELVED_FILES_LOCK.writeLock().unlock()
      notifyStateChanged()
    }
    if (rollback) {
      rollbackChangesAfterShelve(changes, honorExcludedFromCommit)
    }
    return shelveList
  }

  @Throws(VcsException::class, IOException::class)
  private fun createShelfFromChanges(
    changes: MutableCollection<out Change>,
    commitMessage: String,
    markToBeDeleted: Boolean,
    honorExcludedFromCommit: Boolean,
  ): ShelvedChangeList {
    if (changes.isEmpty()) {
      LOG.warn("Creating an empty shelved list", Throwable())
    }
    LOG.debug("Shelving of " + changes.size + " changes...")

    try {
      return myTracer.spanBuilder(Shelve.TotalShelving.getName()).setAttribute("changesSize", changes.size.toLong()).use {
        val schemePatchDir: Path = generateUniqueSchemePatchDir(commitMessage, true)
        val textChanges: MutableList<Change> = ArrayList()
        val binaryFiles: MutableList<ShelvedBinaryFile> = ArrayList()
        for (change in changes) {
          if (ChangesUtil.getFilePath(change).getIOFile().isDirectory()) {
            continue
          }
          if (IdeaTextPatchBuilder.isBinaryRevision(change.getBeforeRevision()) ||
              IdeaTextPatchBuilder.isBinaryRevision(change.getAfterRevision())
          ) {
            binaryFiles.add(shelveBinaryFile(schemePatchDir, change))
          }
          else {
            textChanges.add(change)
          }
        }

        if (textChanges.isEmpty() && binaryFiles.isEmpty()) {
          LOG.warn("Created an empty shelved list, ignored changes: " + changes)
        }

        val patchFile: Path = getPatchFileInConfigDir(schemePatchDir)
        val patches: MutableList<FilePatch> = ArrayList(
          buildAndSavePatchInBatches(
            patchFile,
            textChanges,
            honorExcludedFromCommit
          )
        )

        val changeList: ShelvedChangeList = ShelvedChangeList(
          patchFile, commitMessage.replace('\n', ' '), binaryFiles,
          ShelvedChangeList.createShelvedChangesFromFilePatches(myProject, patchFile, patches)
        )
        changeList.markToDelete(markToBeDeleted)
        changeList.setName(schemePatchDir.getFileName().toString())
        ProgressManager.checkCanceled()
        schemeManager.addScheme(changeList, false)
        changeList
      }
    }
    catch (e: IOException) {
      throw e
    }
    catch (e: VcsException) {
      throw e
    }
    catch (e: RuntimeException) {
      throw e
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  @Throws(IOException::class, VcsException::class)
  private fun buildAndSavePatchInBatches(
    patchFile: Path,
    textChanges: MutableList<Change>,
    honorExcludedFromCommit: Boolean,
  ): MutableList<FilePatch> {
    val patches: MutableList<FilePatch> = ArrayList()
    if (textChanges.isEmpty()) {
      savePatchFile(myProject, patchFile, patches, null, CommitContext())
      return patches
    }

    var batchIndex = 0
    val baseContentsPreloadSize: Int = Registry.intValue("git.shelve.load.base.in.batches", -1)
    val partitionSize = if (baseContentsPreloadSize > 0) baseContentsPreloadSize else textChanges.size
    val partition: List<List<Change>> = Lists.partition(textChanges, partitionSize)
    for (list in partition) {
      batchIndex++
      val finalBatchIndex = batchIndex
      try {
        myTracer.spanBuilder(Shelve.BatchShelving.getName()).setAttribute("batch", finalBatchIndex.toLong()).use {
          try {
            if (baseContentsPreloadSize > 0) {
              myTracer.spanBuilder(Shelve.PreloadingBaseRevisions.getName()).setAttribute("changesSize", list.size.toLong()).use {
                preloadBaseRevisions(list)
              }
            }

            ProgressManager.checkCanceled()
            myTracer.spanBuilder(Shelve.BuildingPatches.getName()).use {
              patches.addAll(
                IdeaTextPatchBuilder
                  .buildPatch(
                    myProject,
                    list,
                    myProject.stateStore.projectBasePath,
                    false,
                    honorExcludedFromCommit
                  )
              )
            }
            ProgressManager.checkCanceled()

            val commitContext: CommitContext = myTracer.spanBuilder(Shelve.StoringBaseRevision.getName()).use {
              val context = CommitContext()
              baseRevisionsOfDvcsIntoContext(list, context)
              context
            }

            myTracer.spanBuilder(Shelve.StoringPathFile.getName()).use {
              savePatchFile(myProject, patchFile, patches, null, commitContext)
            }
          }
          finally {
            ProjectLevelVcsManager.getInstance(myProject).contentRevisionCache.clearConstantCache()
          }
        }
      }
      catch (e: IOException) {
        throw e
      }
      catch (e: VcsException) {
        throw e
      }
      catch (e: RuntimeException) {
        throw e
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
    return patches
  }

  private fun preloadBaseRevisions(textChanges: List<Change>) {
    val changesGroupedByRoot: MultiMap<VcsRoot?, Change?> = MultiMap.create()
    for (change in textChanges) {
      val beforeRevision: ContentRevision? = change.getBeforeRevision()
      if (beforeRevision != null) {
        val file: FilePath? = beforeRevision.getFile()
        val vcsRoot: VcsRoot? = ProjectLevelVcsManager.getInstance(myProject).getVcsRootObjectFor(file)
        if (vcsRoot == null || vcsRoot.getVcs() == null) {
          LOG.error(file.toString() + " is not under VCS")
        }
        else {
          changesGroupedByRoot.putValue(vcsRoot, change)
        }
      }
    }

    for (vcsRoot in changesGroupedByRoot.keySet()) {
      if (vcsRoot == null) continue
      val vcs: AbstractVcs = vcsRoot.vcs ?: continue
      val diffProvider = vcs.diffProvider
      if (diffProvider != null) {
        diffProvider.preloadBaseRevisions(
          Objects.requireNonNull(vcsRoot.path),
          changesGroupedByRoot.get(vcsRoot)
        )
      }
    }
  }

  private fun rollbackChangesAfterShelve(changes: MutableCollection<out Change>, honorExcludedFromCommit: Boolean) {
    val operationName: String? =
      UIUtil.removeMnemonic(RollbackChangesDialog.operationNameByChanges(myProject, changes))
    val modalContext = ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext()
    myTracer.spanBuilder(Shelve.RollbackAfterShelve.getName()).use {
      RollbackWorker(myProject, operationName, modalContext)
        .doRollback(
          changes,
          true,
          null,
          VcsBundle.message("activity.name.shelve"),
          VcsActivity.Shelve,
          honorExcludedFromCommit
        )
    }
  }

  private fun baseRevisionsOfDvcsIntoContext(textChanges: List<Change>, commitContext: CommitContext) {
    val vcsManager: ProjectLevelVcsManager = ProjectLevelVcsManager.getInstance(myProject)
    if (dvcsUsedInProject() && VcsConfiguration.getInstance(myProject).INCLUDE_TEXT_INTO_SHELF) {
      val toKeep: MutableMap<FilePath?, ContentRevision?> = HashMap<FilePath?, ContentRevision?>()
      for (change in textChanges) {
        if (change.getBeforeRevision() == null || change.getAfterRevision() == null) continue
        if (isBig(change)) continue
        val filePath: FilePath? = change.getBeforeRevision()!!.getFile()
        val vcs: AbstractVcs? = vcsManager.getVcsFor(filePath)
        if (vcs != null && VcsType.distributed.equals(vcs.getType())) {
          toKeep.put(filePath, change.getBeforeRevision())
        }
      }
      commitContext.putUserData(BaseRevisionTextPatchEP.ourBaseRevisions, toKeep)
    }
  }

  private fun dvcsUsedInProject(): Boolean {
    return ContainerUtil.exists(
      ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss(),
      { vcs -> VcsType.distributed.equals(vcs.getType()) })
  }

  @Throws(IOException::class)
  fun importFilePatches(
    fileName: String,
    patches: MutableList<out FilePatch>,
    patchTransitExtensions: MutableList<out PatchEP>?,
  ): ShelvedChangeList {
    try {
      val schemePatchDir: Path = generateUniqueSchemePatchDir(fileName, true)
      val patchFile: Path = getPatchFileInConfigDir(schemePatchDir)
      savePatchFile(myProject, patchFile, patches, patchTransitExtensions, CommitContext())
      val changeList: ShelvedChangeList = ShelvedChangeList(
        patchFile, fileName.replace('\n', ' '), SmartList(),
        ShelvedChangeList.createShelvedChangesFromFilePatches(myProject, patchFile, patches)
      )
      changeList.setName(schemePatchDir.getFileName().toString())
      schemeManager.addScheme(changeList, false)
      return changeList
    }
    finally {
      notifyStateChanged()
    }
  }

  fun gatherPatchFiles(files: MutableCollection<out VirtualFile>): MutableList<VirtualFile> {
    val result: MutableList<VirtualFile> = ArrayList()

    val filesQueue: LinkedList<VirtualFile> = LinkedList<VirtualFile>(files)
    while (!filesQueue.isEmpty()) {
      ProgressManager.checkCanceled()
      val file: VirtualFile = filesQueue.removeFirst()
      if (file.isDirectory()) {
        filesQueue.addAll(file.children.asList())
        continue
      }
      if (PatchFileType.isPatchFile(file)) {
        result.add(file)
      }
    }

    return result
  }

  @RequiresBackgroundThread
  fun importChangeLists(
    files: MutableCollection<out VirtualFile>,
    exceptionConsumer: Consumer<in VcsException?>,
  ): MutableList<ShelvedChangeList> {
    val result: MutableList<ShelvedChangeList> = ArrayList(files.size)
    try {
      val filesProgress: FilesProgress = FilesProgress(files.size.toDouble(), VcsBundle.message("shelve.import.to.progress"))
      for (file in files) {
        filesProgress.updateIndicator(file)
        val description: String? = file.getNameWithoutExtension().replace('_', ' ')
        try {
          val schemeNameDir: Path = generateUniqueSchemePatchDir(description, true)
          val patchFile: Path = getPatchFileInConfigDir(schemeNameDir)
          val filePatches: MutableList<out FilePatch> =
            loadPatchesWithoutContent(myProject, file.toNioPath(), CommitContext())
          if (!filePatches.isEmpty()) {
            Files.copy(file.toNioPath(), patchFile)
            val list: ShelvedChangeList = ShelvedChangeList(
              patchFile, description, SmartList(),
              ShelvedChangeList.createShelvedChangesFromFilePatches(myProject, patchFile, filePatches),
              file.getTimeStamp()
            )
            list.setName(schemeNameDir.getFileName().toString())
            schemeManager.addScheme(list, false)
            result.add(list)
          }
        }
        catch (e: Exception) {
          exceptionConsumer.accept(VcsException(e))
        }
      }
    }
    finally {
      notifyStateChanged()
    }
    return result
  }

  @Throws(IOException::class)
  private fun shelveBinaryFile(schemePatchDir: Path, change: Change): ShelvedBinaryFile {
    val beforeRevision: ContentRevision? = change.getBeforeRevision()
    val afterRevision: ContentRevision? = change.getAfterRevision()
    val beforeFile: File? = if (beforeRevision == null) null else beforeRevision.getFile().getIOFile()
    val afterFile: File? = if (afterRevision == null) null else afterRevision.getFile().getIOFile()
    var shelvedPath: String? = null
    if (afterFile != null) {
      val shelvedFileName: String = afterFile.getName()
      val name: String = FileUtilRt.getNameWithoutExtension(shelvedFileName).orEmpty()
      val extension: String = FileUtilRt.getExtension(shelvedFileName).orEmpty()
      val shelvedFile: File = FileUtil.findSequentNonexistentFile(schemePatchDir.toFile(), name, extension)
      FileUtil.copy(afterRevision!!.getFile().getIOFile(), shelvedFile)
      shelvedPath = shelvedFile.getPath()
    }
    val beforePath: String? = ChangesUtil.getProjectRelativePath(myProject, beforeFile)
    val afterPath: String? = ChangesUtil.getProjectRelativePath(myProject, afterFile)
    return ShelvedBinaryFile(beforePath ?: "", afterPath ?: "", shelvedPath)
  }

  private fun notifyStateChanged() {
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(SHELF_TOPIC).shelvedListsChanged()
    }
  }

  @Throws(IOException::class)
  private fun generateUniqueSchemePatchDir(defaultName: String?, createResourceDirectory: Boolean): Path {
    val shelfResourcesDirectory: File = this.shelfResourcesDirectory
    val dir: Path = suggestPatchName(myProject, defaultName, shelfResourcesDirectory, "").toPath()
    if (createResourceDirectory) {
      Files.createDirectories(dir)
    }
    return dir
  }

  @CalledInAny
  fun unshelveChangeList(
    changeList: ShelvedChangeList,
    changes: MutableList<ShelvedChange>?,
    binaryFiles: MutableList<ShelvedBinaryFile>?,
    targetChangeList: LocalChangeList?,
    showSuccessNotification: Boolean,
  ) {
    unshelveChangeList(
      changeList, changes, binaryFiles, targetChangeList, showSuccessNotification,
      this.isRemoveFilesFromShelf
    )
  }

  @CalledInAny
  private fun unshelveChangeList(
    changeList: ShelvedChangeList,
    changes: MutableList<ShelvedChange>?,
    binaryFiles: MutableList<ShelvedBinaryFile>?,
    targetChangeList: LocalChangeList?,
    showSuccessNotification: Boolean,
    removeFilesFromShelf: Boolean,
  ): ApplyPatchStatus? {
    return unshelveChangeList(
      changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, false, false, null, null,
      removeFilesFromShelf
    )
  }

  @CalledInAny
  fun unshelveChangeList(
    changeList: ShelvedChangeList,
    changes: MutableList<ShelvedChange>?,
    binaryFiles: MutableList<ShelvedBinaryFile>?,
    targetChangeList: LocalChangeList?,
    showSuccessNotification: Boolean,
    systemOperation: Boolean,
    reverse: Boolean,
    @NlsContexts.Label leftConflictTitle: String?,
    @NlsContexts.Label rightConflictTitle: String?,
    removeFilesFromShelf: Boolean,
  ): ApplyPatchStatus? {
    return unshelveChangeList(
      changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, systemOperation, reverse,
      leftConflictTitle, rightConflictTitle, removeFilesFromShelf, true
    )
  }

  @CalledInAny
  fun unshelveChangeList(
    changeList: ShelvedChangeList,
    changes: MutableList<ShelvedChange>?,
    binaryFiles: MutableList<ShelvedBinaryFile>?,
    targetChangeList: LocalChangeList?,
    showSuccessNotification: Boolean,
    systemOperation: Boolean,
    reverse: Boolean,
    @NlsContexts.Label leftConflictTitle: String?,
    @NlsContexts.Label rightConflictTitle: String?,
    removeFilesFromShelf: Boolean,
    reportLocalHistoryActivity: Boolean,
  ): ApplyPatchStatus? {
    val remainingPatches: MutableList<FilePatch> = ArrayList()

    val commitContext: CommitContext = CommitContext()
    commitContext.putUserData(BaseRevisionTextPatchEP.ourProvideStoredBaseRevisionTextKey, true)

    val textFilePatches: MutableList<TextFilePatch>
    try {
      textFilePatches = loadTextPatches(myProject, changeList, changes, remainingPatches, commitContext)
    }
    catch (e: IOException) {
      LOG.info(e)
      PatchApplier.showError(myProject, VcsBundle.message("unshelve.loading.patch.error", e.message))
      return ApplyPatchStatus.FAILURE
    }
    catch (e: PatchSyntaxException) {
      LOG.info(e)
      PatchApplier.showError(myProject, VcsBundle.message("unshelve.loading.patch.error", e.message))
      return ApplyPatchStatus.FAILURE
    }

    val patches: MutableList<FilePatch> = ArrayList(textFilePatches)

    val remainingBinaries: MutableList<ShelvedBinaryFile> = ArrayList()
    val binaryFilesToUnshelve: MutableList<ShelvedBinaryFile> =
      getBinaryFilesToUnshelve(changeList, binaryFiles, remainingBinaries)

    for (shelvedBinaryFile in binaryFilesToUnshelve) {
      patches.add(ShelvedBinaryFilePatch(shelvedBinaryFile))
    }

    val baseDir: VirtualFile? =
      LocalFileSystem.getInstance().findFileByNioFile(myProject.stateStore.projectBasePath)
    val activityId: ActivityId? = if (reportLocalHistoryActivity) VcsActivity.Unshelve else null
    val patchApplier: PatchApplier = PatchApplier(
      myProject, baseDir!!,
      patches, targetChangeList, commitContext, reverse, leftConflictTitle,
      rightConflictTitle, VcsBundle.message("activity.name.unshelve"), activityId
    )
    val status: ApplyPatchStatus? = patchApplier.execute(showSuccessNotification, systemOperation)
    if (removeFilesFromShelf) {
      remainingPatches.addAll(patchApplier.getRemainingPatches())
      remainingPatches.addAll(patchApplier.getFailedPatches())
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), myProject.getDisposed(), {
        updateListAfterUnshelve(changeList, remainingPatches, remainingBinaries, commitContext)
      })
    }
    return status
  }

  @RequiresEdt
  @VisibleForTesting
  @ApiStatus.Internal
  fun deleteShelves(
    shelvedListsToDelete: List<ShelvedChangeList>,
    shelvedListsFromChanges: List<ShelvedChangeList>,
    changesToDelete: List<ShelvedChange>,
    binariesToDelete: List<ShelvedBinaryFile>,
  ): MutableMap<ShelvedChangeList, Date> {
    // filter changes
    val shelvedListsFromChangesToDelete: MutableList<ShelvedChangeList> = ArrayList(shelvedListsFromChanges)
    shelvedListsFromChangesToDelete.removeAll(shelvedListsToDelete)

    if (shelvedListsFromChangesToDelete.size + binariesToDelete.size == 0 && shelvedListsToDelete.isEmpty()) {
      return mutableMapOf()
    }

    //store original dates to restore if needed
    val deletedListsWithOriginalDate: MutableMap<ShelvedChangeList, Date> = HashMap()
    for (changeList in shelvedListsToDelete) {
      val originalDate: Date = changeList.getDate()
      if (changeList.isDeleted()) {
        deleteChangeListCompletely(changeList)
      }
      else {
        markChangeListAsDeleted(changeList)
        deletedListsWithOriginalDate.put(changeList, originalDate)
      }
    }

    for (list in shelvedListsFromChangesToDelete) {
      val originalDate: Date = list.getDate()
      val wasDeleted: Boolean = list.isDeleted()
      val newListWithDeletedChanges: ShelvedChangeList? =
        removeChangesFromChangeList(list, changesToDelete, binariesToDelete)
      if (newListWithDeletedChanges != null) {
        deletedListsWithOriginalDate.put(newListWithDeletedChanges, originalDate)
      }
      else if (!wasDeleted) {
        //entire list became deleted because no changes remained
        val shelvedChangeList: ShelvedChangeList? = schemeManager.findSchemeByName(list.getName())
        if (shelvedChangeList != null && shelvedChangeList.isDeleted()) {
          deletedListsWithOriginalDate.put(shelvedChangeList, originalDate)
        }
      }
    }
    return deletedListsWithOriginalDate
  }

  @RequiresEdt
  private fun removeChangesFromChangeList(
    list: ShelvedChangeList,
    changes: List<ShelvedChange>,
    binaryFiles: List<ShelvedBinaryFile>,
  ): ShelvedChangeList? {
    val remainingBinaries: MutableList<ShelvedBinaryFile> = ArrayList(list.getBinaryFiles())
    remainingBinaries.removeAll(binaryFiles)

    val commitContext: CommitContext = CommitContext()
    commitContext.putUserData(BaseRevisionTextPatchEP.ourProvideStoredBaseRevisionTextKey, true)

    val remainingPatches: MutableList<FilePatch> = ArrayList()
    try {
      loadTextPatches(myProject, list, changes, remainingPatches, commitContext)
    }
    catch (e: IOException) {
      LOG.info(e)
      VcsImplUtil.showErrorMessage(
        myProject, e.message,
        VcsBundle.message("shelve.delete.files.from.changelist.error", list.getDescription())
      )
      return null
    }
    catch (e: PatchSyntaxException) {
      LOG.info(e)
      VcsImplUtil.showErrorMessage(
        myProject, e.message,
        VcsBundle.message("shelve.delete.files.from.changelist.error", list.getDescription())
      )
      return null
    }
    return saveRemainingPatchesIfNeeded(list, remainingPatches, remainingBinaries, commitContext, true)
  }

  var isRemoveFilesFromShelf: Boolean
    get() = myState.myRemoveFilesFromShelf
    set(removeFilesFromShelf) {
      myState.myRemoveFilesFromShelf = removeFilesFromShelf
    }

  private fun markDeletedSystemUnshelved() {
    val systemUnshelved: MutableList<ShelvedChangeList> =
      ContainerUtil.filter(
        schemeManager.allSchemes,
        { list: ShelvedChangeList -> list.isRecycled() && list.isMarkedToDelete() })
    for (list in systemUnshelved) {
      list.setDeleted(true)
      list.markToDelete(false)
    }
  }

  private fun cleanDeletedOlderOneWeek() {
    val cal: Calendar = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -7)
    clean(Condition { list -> (list.isDeleted() && list.getDate().before(Date(cal.getTimeInMillis()))) })
  }

  fun cleanUnshelved(timeBefore: Long) {
    val limitDate: Date = Date(timeBefore)
    clean(Condition { l -> l.isRecycled() && l.getDate().before(limitDate) })
  }

  private fun clean(condition: Condition<in ShelvedChangeList>) {
    val toDelete: MutableList<ShelvedChangeList> = ContainerUtil.filter(schemeManager.allSchemes, condition)
    clearShelvedLists(toDelete, true)
  }

  @RequiresEdt
  fun shelveSilentlyUnderProgress(changes: List<Change>, rollbackChanges: Boolean) {
    val result: MutableList<ShelvedChangeList> = ArrayList()
    object : Task.Backgroundable(myProject, VcsBundle.message("shelve.changes.progress.title"), true) {
      public override fun run(indicator: ProgressIndicator) {
        result.addAll(shelveChangesSilentlyInSeparatedLists(changes.toMutableList(), rollbackChanges, indicator))
      }

      public override fun onSuccess() {
        VcsNotifier.getInstance(this@ShelveChangesManager.myProject).notifySuccess(
          VcsNotificationIdsHolder.SHELVE_SUCCESSFUL, "",
          VcsBundle.message("shelve.successful.message")
        )
        if (result.size == 1 && this@ShelveChangesManager.isShelfContentActive) {
          ShelvedChangesViewManager.getInstance(this@ShelveChangesManager.myProject).startEditing(result[0])
        }
      }
    }.queue()
  }

  private fun rememberShelvingFiles(changes: MutableCollection<out Change>) {
    myShelvingFiles = HashSet(ContainerUtil.map2SetNotNull(changes, Change::getVirtualFile))
  }

  private fun cleanShelvingFiles() {
    myShelvingFiles = null
  }

  private val isShelfContentActive: Boolean
    get() {
      val window: ToolWindow? = ChangesViewContentManager.getToolWindowFor(myProject, ChangesViewContentManager.SHELF)
      return window != null &&
             window.isVisible() &&
             (ChangesViewContentManager.getInstance(myProject) as ChangesViewContentManager).isContentSelected(
               ChangesViewContentManager.SHELF
             )
    }

  private fun shelveChangesSilentlyInSeparatedLists(
    changes: MutableCollection<out Change>,
    rollbackChanges: Boolean,
    indicator: ProgressIndicator,
  ): MutableList<ShelvedChangeList> {
    val failedChangeLists: MutableList<String?> = ArrayList<String?>()
    val result: MutableList<ShelvedChangeList> = ArrayList<ShelvedChangeList>()
    val shelvedChanges: MutableList<Change> = ArrayList<Change>()

    try {
      val changeListManager: ChangeListManager = ChangeListManager.getInstance(myProject)
      if (!changeListManager.areChangeListsEnabled()) {
        LOG.warn("Changelists are disabled", Throwable())
      }

      SHELVED_FILES_LOCK.writeLock().lock()
      rememberShelvingFiles(changes)
      val changeLists: MutableList<LocalChangeList> = changeListManager.getChangeLists()
      for (list in changeLists) {
        val changeSet: MutableSet<Change> = HashSet(list.getChanges())

        val changesForChangelist: MutableList<Change> = ArrayList<Change>()
        for (change in changes) {
          val inChangelist: Boolean
          if (change is ChangeListChange) {
            inChangelist = change.changeListId == list.id
          }
          else {
            inChangelist = changeSet.contains(change)
          }

          if (inChangelist) {
            changesForChangelist.add(change)
          }
        }

        if (!changesForChangelist.isEmpty()) {
          try {
            val suggestedTitle: String? =
              ShelveSilentlyTitleProvider.suggestTitle(myProject, changesForChangelist)
            result.add(
              createShelfFromChanges(
                changesForChangelist,
                if (suggestedTitle == null) list.getName() else suggestedTitle,
                false,
                false
              )
            )
            shelvedChanges.addAll(changesForChangelist)
          }
          catch (e: Exception) {
            indicator.checkCanceled()
            LOG.warn(e)
            failedChangeLists.add(list.getName())
          }
        }
      }
    }
    finally {
      cleanShelvingFiles()
      SHELVED_FILES_LOCK.writeLock().unlock()
      notifyStateChanged()
    }

    if (rollbackChanges) {
      rollbackChangesAfterShelve(shelvedChanges, false)
    }

    if (!failedChangeLists.isEmpty()) {
      VcsNotifier.getInstance(myProject).notifyError(
        VcsNotificationIdsHolder.SHELVE_FAILED,
        VcsBundle.message("shelve.failed.title"),
        VcsBundle.message(
          "shelve.failed.message",
          failedChangeLists.size,
          HtmlBuilder().appendWithSeparators(
            HtmlChunk.text(","),
            ContainerUtil.map(failedChangeLists, HtmlChunk::text)
          )
            .toString()
        )
      )
    }
    return result
  }

  fun unshelveSilentlyAsynchronously(
    project: Project,
    selectedChangeLists: List<ShelvedChangeList>,
    selectedChanges: List<ShelvedChange>,
    selectedBinaryChanges: List<ShelvedBinaryFile>,
    forcePredefinedOneChangelist: LocalChangeList?,
  ) {
    unshelveSilentlyAsynchronously(
      project, selectedChangeLists, selectedChanges, selectedBinaryChanges, forcePredefinedOneChangelist,
      this.isRemoveFilesFromShelf
    )
  }

  fun unshelveSilentlyAsynchronously(
    project: Project,
    selectedChangeLists: List<ShelvedChangeList>,
    selectedChanges: List<ShelvedChange>,
    selectedBinaryChanges: List<ShelvedBinaryFile>,
    forcePredefinedOneChangelist: LocalChangeList?, removeFilesFromShelf: Boolean,
  ) {
    ProgressManager.getInstance()
      .run(object : Task.Backgroundable(project, VcsBundle.message("unshelve.changes.progress.title"), true) {
        public override fun run(indicator: ProgressIndicator) {
          for (changeList in selectedChangeLists) {
            val changesForChangelist = ArrayList(ContainerUtil.intersection(changeList.getChanges()!!, selectedChanges))
            val binariesForChangelist = ArrayList(ContainerUtil.intersection(changeList.getBinaryFiles(), selectedBinaryChanges))
            val shouldUnshelveAllList = changesForChangelist.isEmpty() && binariesForChangelist.isEmpty()
            val status: ApplyPatchStatus? = unshelveChangeList(
              changeList,
              if (shouldUnshelveAllList) null else changesForChangelist,
              if (shouldUnshelveAllList) null else binariesForChangelist,
              if (forcePredefinedOneChangelist != null) forcePredefinedOneChangelist
              else getChangeListUnshelveTo(
                changeList
              ),
              true,
              removeFilesFromShelf
            )
            ChangeListManagerEx.getInstanceEx(project).waitForUpdate()

            if (status === ApplyPatchStatus.ABORT) {
              break
            }
          }
        }
      })
  }

  private fun getChangeListUnshelveTo(list: ShelvedChangeList): LocalChangeList? {
    val manager: ChangeListManager = ChangeListManager.getInstance(myProject)
    if (!manager.areChangeListsEnabled()) return null
    if (!VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY) return null
    val localChangeList: LocalChangeList? = getPredefinedChangeList(list, manager)
    return if (localChangeList != null) localChangeList
    else manager.addChangeList(
      getChangeListNameForUnshelve(list),
      ""
    )
  }

  @RequiresEdt
  fun updateListAfterUnshelve(
    listToUpdate: ShelvedChangeList,
    patches: MutableList<out FilePatch>,
    binaries: MutableList<ShelvedBinaryFile>,
    commitContext: CommitContext,
  ) {
    saveRemainingPatchesIfNeeded(listToUpdate, patches, binaries, commitContext, false)
  }

  /**
   * Return newly created shelved list with applied (deleted or unshelved) changes or null if no additional shelved list was created
   * 1. if no changes remained in the original list - delete or mark applied (recycled) entire list - > no new list created, return null;
   * 2. if there are some applied (deleted) changes and something remained it the original list then create separated list for applied
   * changes and delete these changes from the original list - > in this case new list with applied (deleted) changes will be a return value
   */
  @RequiresEdt
  private fun saveRemainingPatchesIfNeeded(
    changeList: ShelvedChangeList,
    remainingPatches: MutableList<out FilePatch>,
    remainingBinaries: MutableList<ShelvedBinaryFile>,
    commitContext: CommitContext,
    delete: Boolean,
  ): ShelvedChangeList? {
    // all changes in the shelved list have been chosen to be applied/deleted
    if (remainingPatches.isEmpty() && remainingBinaries.isEmpty()) {
      if (!delete) {
        recycleChangeList(changeList)
      }
      else if (changeList.isDeleted()) {
        deleteChangeListCompletely(changeList)
      }
      else {
        markChangeListAsDeleted(changeList)
      }
      return null
    }
    //apply already applied  - do not change anything
    if (!delete && changeList.isRecycled()) return null

    var newlyCreatedList: ShelvedChangeList? = null
    if ((delete && changeList.isDeleted())) {
      saveRemainingChangesInList(changeList, remainingPatches, remainingBinaries, commitContext)
    }
    else {
      newlyCreatedList =
        saveRemainingAndRecycleOthers(changeList, remainingPatches, remainingBinaries, commitContext, delete)
    }
    notifyStateChanged()
    return newlyCreatedList
  }

  /**
   * Delete applied/deleted changes from original list and create recycled/delete copy with others
   *
   * @return newly created recycled/deleted list or null if no new list was created
   */
  @RequiresEdt
  private fun saveRemainingAndRecycleOthers(
    changeList: ShelvedChangeList,
    remainingPatches: MutableList<out FilePatch>,
    remainingBinaries: MutableList<ShelvedBinaryFile>?,
    commitContext: CommitContext,
    delete: Boolean,
  ): ShelvedChangeList? {
    try {
      val listCopy: ShelvedChangeList = createChangelistCopyWithChanges(
        changeList,
        generateUniqueSchemePatchDir(changeList.getDescription(), true)
      )
      listCopy.updateDate()
      //changes should be loaded
      saveRemainingChangesInList(changeList, remainingPatches, remainingBinaries, commitContext)

      removeFromListWithChanges(listCopy, Objects.requireNonNull(changeList.getChanges()), changeList.getBinaryFiles(), commitContext)
      if (delete) {
        markChangeListAsDeleted(listCopy)
      }
      else {
        recycleChangeList(listCopy)
      }
      saveListAsScheme(listCopy)
      return listCopy
    }
    catch (e: IOException) {
      // do not delete if cannot recycle
      return null
    }
  }

  private fun saveRemainingChangesInList(
    changeList: ShelvedChangeList,
    remainingPatches: MutableList<out FilePatch>,
    remainingBinaries: MutableList<ShelvedBinaryFile>?, commitContext: CommitContext?,
  ) {
    writePatchesToFile(myProject, changeList.getPath()!!, remainingPatches, commitContext)

    if (remainingBinaries != null) {
      changeList.getBinaryFiles().retainAll(remainingBinaries)
    }
    changeList.setChanges(ShelvedChangeList.createShelvedChangesFromFilePatches(myProject, changeList.getPath()!!, remainingPatches))
  }

  @ApiStatus.Internal
  @VisibleForTesting
  fun saveListAsScheme(list: ShelvedChangeList) {
    if (!list.getBinaryFiles().isEmpty() || !ContainerUtil.isEmpty(list.getChanges())) {
      // all newly create ShelvedChangeList have to be added to SchemesManger as new scheme
      schemeManager.addScheme(list, false)
    }
  }

  @ApiStatus.Internal
  @VisibleForTesting
  @Throws(IOException::class)
  fun createChangelistCopyWithChanges(changeList: ShelvedChangeList, targetDir: Path): ShelvedChangeList {
    val newPath: Path = getPatchFileInConfigDir(targetDir)
    Files.createDirectories(newPath.getParent())
    Files.copy(changeList.getPath(), newPath)
    changeList.loadChangesIfNeeded(myProject)

    val listCopy: ShelvedChangeList = ShelvedChangeList(
      newPath, changeList.getDescription(),
      copyBinaryFiles(changeList, targetDir),
      copyTextFiles(myProject, changeList, newPath),
      changeList.getDate().getTime()
    )
    listCopy.markToDelete(changeList.isMarkedToDelete())
    listCopy.setRecycled(changeList.isRecycled())
    listCopy.setDeleted(changeList.isDeleted())
    listCopy.setName(targetDir.getFileName().toString())
    return listCopy
  }

  fun restoreList(shelvedChangeList: ShelvedChangeList, restoreDate: Date) {
    val list: ShelvedChangeList? = schemeManager.findSchemeByName(shelvedChangeList.getName())
    if (list == null) {
      return
    }
    list.setDeleted(false)
    list.setDate(restoreDate)
    notifyStateChanged()
  }

  val recycledShelvedChangeLists: List<ShelvedChangeList>
    get() = getRecycled(true)

  val deletedLists: List<ShelvedChangeList>
    get() = ContainerUtil.filter(schemeManager.allSchemes, ShelvedChangeList::isDeleted).toList()

  @ApiStatus.Internal
  @VisibleForTesting
  fun clearShelvedLists(shelvedLists: MutableList<ShelvedChangeList>, updateView: Boolean) {
    if (shelvedLists.isEmpty()) return
    for (list in shelvedLists) {
      deleteResources(list)
      schemeManager.removeScheme(list)
    }
    if (updateView) {
      notifyStateChanged()
    }
  }

  val shelvingFiles: MutableCollection<VirtualFile>
    get() = HashSet(ContainerUtil.notNullize(myShelvingFiles))

  private fun removeFromListWithChanges(
    listCopy: ShelvedChangeList,
    shelvedChanges: MutableList<ShelvedChange>,
    shelvedBinaryChanges: MutableList<ShelvedBinaryFile>,
    commitContext: CommitContext,
  ) {
    //listCopy should contain loaded changes
    removeBinaries(listCopy, shelvedBinaryChanges)
    removeChanges(listCopy, shelvedChanges)

    // create patch file based on filtered changes
    try {
      val patches: MutableList<FilePatch> = ArrayList()
      val filePatches: MutableList<TextFilePatch> = loadPatches(myProject, listCopy.getPath()!!, commitContext)
      for (change in Objects.requireNonNull(listCopy.getChanges())) {
        val patch =
          ContainerUtil.find(
            filePatches,
            { patch -> change.getBeforePath().equals(patch.getBeforeName()) })
        if (patch != null) patches.add(patch)
      }
      writePatchesToFile(myProject, listCopy.getPath()!!, patches, commitContext)
    }
    catch (e: IOException) {
      LOG.info(e)
      // left file as is
    }
    catch (e: PatchSyntaxException) {
      LOG.info(e)
    }
  }

  private fun recycleChangeList(changeList: ShelvedChangeList) {
    changeList.setRecycled(true)
    changeList.updateDate()
    if (changeList.isMarkedToDelete()) {
      changeList.markToDelete(false)
      changeList.setDeleted(true)
    }
    notifyStateChanged()
  }

  private fun deleteChangeListCompletely(changeList: ShelvedChangeList) {
    deleteResources(changeList)
    schemeManager.removeScheme(changeList)
    notifyStateChanged()
  }

  @VisibleForTesting
  @ApiStatus.Internal
  fun markChangeListAsDeleted(changeList: ShelvedChangeList) {
    changeList.setDeleted(true)
    changeList.updateDate()
    notifyStateChanged()
  }

  private fun deleteResources(changeList: ShelvedChangeList) {
    try {
      Files.deleteIfExists(changeList.getPath())
    }
    catch (ignore: IOException) {
    }
    for (binaryFile in changeList.getBinaryFiles()) {
      val path: String? = binaryFile.SHELVED_PATH
      if (path != null) {
        FileUtil.delete(File(path))
      }
    }
    //schema dir may be related to another list, so check that it's empty first
    val schemaDir: File = File(this.shelfResourcesDirectory, changeList.getName())
    if (schemaDir.exists() && ArrayUtil.isEmpty(schemaDir.list())) {
      FileUtil.delete(schemaDir)
    }
  }

  fun renameChangeList(changeList: ShelvedChangeList, newName: String?) {
    changeList.setDescription(newName.orEmpty())
    notifyStateChanged()
  }

  var isShowRecycled: Boolean
    get() = myState.myShowRecycled
    set(showRecycled) {
      myState.myShowRecycled = showRecycled
    }

  var grouping: MutableCollection<String>
    get() = myState.groupingKeys
    set(grouping) {
      myState.groupingKeys.clear()
      myState.groupingKeys.addAll(grouping)
    }

  @ApiStatus.Internal
  class State {
    @OptionTag("remove_strategy")
    var myRemoveFilesFromShelf: Boolean = false

    @Attribute("show_recycled")
    var myShowRecycled: Boolean = false

    @XCollection
    @JvmField
    var groupingKeys: TreeSet<String> = TreeSet()
  }

  companion object {
    const val DEFAULT_PROJECT_PRESENTATION_PATH: String = "<Project>/shelf" //NON-NLS

    @Topic.ProjectLevel
    @JvmField
    val SHELF_TOPIC: Topic<ShelveChangesManagerListener> =
      Topic("shelf updates", ShelveChangesManagerListener::class.java, Topic.BroadcastDirection.NONE)
    private val LOG: Logger = Logger.getInstance(ShelveChangesManager::class.java)

    @NonNls
    private const val ELEMENT_CHANGELIST: @NonNls String = "changelist"

    @NonNls
    private const val ELEMENT_RECYCLED_CHANGELIST: @NonNls String = "recycled_changelist"

    @NonNls
    private const val DEFAULT_PATCH_NAME: @NonNls String = "shelved"
    private const val SHELVE_MANAGER_DIR_PATH = "shelf" //NON-NLS
    private val EMPTY_ELEMENT: Element = Element("state")

    @JvmStatic
    fun getInstance(project: Project): ShelveChangesManager {
      return project.getService(ShelveChangesManager::class.java)
    }

    /**
     * System independent file-path for non-default project
     *
     * @return path to default shelf directory e.g. `"<Project>/.idea/shelf"`
     */
    @JvmStatic
    fun getDefaultShelfPath(project: Project): Path {
      val store = project.stateStore
      return store.projectFilePath.parent.resolve(
        if (project.isDirectoryBased)
          SHELVE_MANAGER_DIR_PATH
        else
          "." + SHELVE_MANAGER_DIR_PATH
      )
    }

    /**
     * System independent file-path for non-default project
     *
     * @return path to custom shelf directory if set. Otherwise return default shelf directory e.g. `"<Project>/.idea/shelf"`
     */
    @JvmStatic
    fun getShelfPath(project: Project): String {
      val vcsConfiguration: VcsConfiguration = VcsConfiguration.getInstance(project)
      if (vcsConfiguration.USE_CUSTOM_SHELF_PATH) {
        return Objects.requireNonNull<String>(vcsConfiguration.CUSTOM_SHELF_PATH)
      }
      return getDefaultShelfPath(project).toString().replace(File.separatorChar, '/')
    }

    private fun copyTextFiles(
      project: Project,
      changeList: ShelvedChangeList,
      newPatchPath: Path,
    ): MutableList<ShelvedChange?> {
      val copied: MutableList<ShelvedChange?> = ArrayList<ShelvedChange?>()
      for (change in changeList.getChanges()!!) {
        copied.add(ShelvedChange.copyToNewPatch(project, newPatchPath, change))
      }
      return copied
    }

    @Throws(IOException::class)
    private fun copyBinaryFiles(list: ShelvedChangeList, targetDirectory: Path): MutableList<ShelvedBinaryFile?> {
      Files.createDirectories(targetDirectory)
      val copied: MutableList<ShelvedBinaryFile?> = ArrayList<ShelvedBinaryFile?>()
      for (file in list.getBinaryFiles()) {
        if (file.SHELVED_PATH != null) {
          val shelvedFile: Path = Paths.get(file.SHELVED_PATH)
          if (!StringUtil.isEmptyOrSpaces(file.AFTER_PATH) && Files.exists(shelvedFile)) {
            val newShelvedFile: Path = targetDirectory.resolve(PathUtil.getFileName(file.AFTER_PATH))
            try {
              Files.copy(shelvedFile, newShelvedFile)
              copied.add(
                ShelvedBinaryFile(
                  file.BEFORE_PATH, file.AFTER_PATH,
                  FileUtil.toSystemIndependentName(newShelvedFile.toString())
                )
              )
            }
            catch (e: IOException) {
              LOG.warn("Can't copy binary file: " + list.getPath(), e)
            }
          }
        }
      }
      return copied
    }

    private fun getPatchFileInConfigDir(schemePatchDir: Path): Path {
      return schemePatchDir.resolve(DEFAULT_PATCH_NAME + "." + VcsConfiguration.PATCH)
    }

    private fun isBig(change: Change): Boolean {
      val vf: VirtualFile? = change.getVirtualFile()
      if (vf != null) {
        return isBig(vf.getLength())
      }

      val beforeRevision: ContentRevision? = change.getBeforeRevision()
      if (beforeRevision != null) {
        try {
          val content: String? = beforeRevision.getContent()
          if (content != null && Companion.isBig(content.length.toLong())) {
            return true
          }
        }
        catch (e: VcsException) {
          LOG.info(e)
        }
      }
      return false
    }

    private fun isBig(contentLength: Long): Boolean {
      return contentLength > VcsConfiguration.ourMaximumFileForBaseRevisionSize
    }

    // for create patch only
    @JvmStatic
    fun suggestPatchName(project: Project, commitMessage: String?, file: File?, extension: String?): File {
      @NonNls var defaultPath: @NonNls String = shortenAndSanitize(commitMessage)
      while (true) {
        val nonexistentFile: File = FileUtil.findSequentNonexistentFile(
          file!!, defaultPath,
          if (extension == null)
            VcsConfiguration.getInstance(project).getPatchFileExtension()
          else
            extension
        )
        if (nonexistentFile.getName().length >= PatchNameChecker.MAX) {
          defaultPath = defaultPath.substring(0, defaultPath.length - 1)
          continue
        }
        return nonexistentFile
      }
    }

    private fun shortenAndSanitize(commitMessage: String?): String {
      @NonNls var defaultPath: @NonNls String = PathUtil.suggestFileName(notNullize(commitMessage))
      if (defaultPath.isEmpty()) {
        defaultPath = "unnamed"
      }
      if (defaultPath.length > PatchNameChecker.MAX - 10) {
        defaultPath = defaultPath.substring(0, PatchNameChecker.MAX - 10)
      }
      return defaultPath
    }

    @Throws(IOException::class, PatchSyntaxException::class)
    private fun loadTextPatches(
      project: Project,
      changeList: ShelvedChangeList,
      changes: List<ShelvedChange>?,
      remainingPatches: MutableList<in FilePatch>,
      commitContext: CommitContext?,
    ): MutableList<TextFilePatch> {
      var textFilePatches: MutableList<TextFilePatch> =
        loadPatches(project, changeList.getPath()!!, commitContext)

      if (changes != null) {
        textFilePatches = ContainerUtil.filter(textFilePatches, { patch ->
          if (needUnshelve(patch, changes)) {
            return@filter true
          }
          else {
            remainingPatches.add(patch)
            return@filter false
          }
        })
      }
      return textFilePatches
    }

    @ApiStatus.Internal
    @RequiresEdt
    fun unshelveSilentlyWithDnd(
      project: Project,
      shelvedChangeListDragBean: ShelvedChangeListDragBean,
      targetChangeList: LocalChangeList?,
      removeFilesFromShelf: Boolean,
    ) {
      FileDocumentManager.getInstance().saveAllDocuments()
      getInstance(project).unshelveSilentlyAsynchronously(
        project, shelvedChangeListDragBean.getShelvedChangelists(),
        shelvedChangeListDragBean.getChanges(),
        shelvedChangeListDragBean.getBinaryFiles(), targetChangeList,
        removeFilesFromShelf
      )
    }

    private fun getBinaryFilesToUnshelve(
      changeList: ShelvedChangeList,
      binaryFiles: MutableList<ShelvedBinaryFile>?,
      remainingBinaries: MutableList<in ShelvedBinaryFile>,
    ): MutableList<ShelvedBinaryFile> {
      if (binaryFiles == null) {
        return ArrayList(changeList.getBinaryFiles())
      }

      val result: MutableList<ShelvedBinaryFile> = ArrayList()
      for (file in changeList.getBinaryFiles()) {
        if (binaryFiles.contains(file)) {
          result.add(file)
        }
        else {
          remainingBinaries.add(file)
        }
      }
      return result
    }

    private fun needUnshelve(patch: FilePatch, changes: List<ShelvedChange>): Boolean {
      for (change in changes) {
        if (patch.getBeforeName() == change.getBeforePath()) {
          return true
        }
      }
      return false
    }

    private fun writePatchesToFile(
      project: Project?,
      path: Path,
      remainingPatches: MutableList<out FilePatch>,
      commitContext: CommitContext?,
    ) {
      try {
        Files.newBufferedWriter(path).use { writer ->
          UnifiedDiffWriter.write(project, remainingPatches, writer, "\n", commitContext)
        }
      }
      catch (e: IOException) {
        LOG.error(e)
      }
    }

    private fun removeChanges(list: ShelvedChangeList, shelvedChanges: MutableList<ShelvedChange>) {
      val iterator: MutableIterator<ShelvedChange> = Objects.requireNonNull(list.getChanges()).iterator()
      while (iterator.hasNext()) {
        val change: ShelvedChange = iterator.next()

        val toRemove: Boolean = ContainerUtil.exists(shelvedChanges, { newChange ->
          change.getBeforePath() == newChange.getBeforePath() &&
          change.getAfterPath() == newChange.getAfterPath()
        }
        )
        if (toRemove) {
          iterator.remove()
        }
      }
    }

    private fun removeBinaries(list: ShelvedChangeList, binaryFiles: MutableList<ShelvedBinaryFile>) {
      val shelvedChangeListIterator: MutableIterator<ShelvedBinaryFile> = list.getBinaryFiles().iterator()
      while (shelvedChangeListIterator.hasNext()) {
        val binaryFile: ShelvedBinaryFile = shelvedChangeListIterator.next()
        for (newBinary in binaryFiles) {
          if (newBinary.BEFORE_PATH == binaryFile.BEFORE_PATH
              && newBinary.AFTER_PATH == binaryFile.AFTER_PATH
          ) {
            shelvedChangeListIterator.remove()
          }
        }
      }
    }

    @Throws(IOException::class, PatchSyntaxException::class)
    @JvmStatic
    fun loadPatches(
      project: Project,
      patchPath: Path,
      commitContext: CommitContext?,
    ): MutableList<TextFilePatch> {
      return loadPatches(project, patchPath, commitContext, true)
    }

    @Throws(IOException::class, PatchSyntaxException::class)
    @JvmStatic
    fun loadPatchesWithoutContent(
      project: Project,
      patchPath: Path,
      commitContext: CommitContext?,
    ): MutableList<out FilePatch> {
      return loadPatches(project, patchPath, commitContext, false)
    }

    @Throws(IOException::class, PatchSyntaxException::class)
    private fun loadPatches(
      project: Project,
      patchPath: Path,
      commitContext: CommitContext?,
      loadContent: Boolean,
    ): MutableList<TextFilePatch> {
      val text: CharArray
      InputStreamReader(Files.newInputStream(patchPath), StandardCharsets.UTF_8).use { reader ->
        text = FileUtilRt.loadText(reader, Files.size(patchPath).toInt())
      }
      if (text.size == 0) return mutableListOf() // shelves generate an empty patch file for shelves that have only binary files

      val reader: PatchReader = PatchReader(CharArrayCharSequence(text, 0, text.size), loadContent)
      val textFilePatches: MutableList<TextFilePatch> = reader.readTextPatches()
      ApplyPatchDefaultExecutor.applyAdditionalInfoBefore(project, reader.getAdditionalInfo(null), commitContext)
      return textFilePatches
    }

    @Throws(IOException::class)
    private fun savePatchFile(
      project: Project,
      patchFile: Path,
      patches: MutableList<out FilePatch>,
      extensions: MutableList<out PatchEP>?,
      context: CommitContext,
    ) {
      Files.newBufferedWriter(patchFile).use { writer ->
        UnifiedDiffWriter.write(
          project,
          project.stateStore.projectBasePath,
          patches,
          writer,
          "\n",
          context,
          extensions
        )
      }
    }
  }
}
