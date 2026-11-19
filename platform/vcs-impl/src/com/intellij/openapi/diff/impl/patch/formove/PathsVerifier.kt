// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch.formove

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchFactory
import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.ex.FileTypeChooser
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
class PathsVerifier(
  private val project: Project,
  private val baseDirectory: VirtualFile,
  private val patches: MutableList<FilePatch>,
) {
  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  // temp
  private val movedFiles = HashMap<VirtualFile, MovedFileData>()
  private val beforePaths = ArrayList<FilePath>()
  private val createdDirectories = ArrayList<VirtualFile>()
  private lateinit var delayedPrecheckContext: DelayedPrecheckContext

  // out
  val textPatches: List<PatchAndFile>
    field = ArrayList<PatchAndFile>()
  val binaryPatches: List<PatchAndFile>
    field = ArrayList<PatchAndFile>()
  val writableFiles: List<VirtualFile>
    field = ArrayList<VirtualFile>()
  val skipped: List<FilePatch>
    field = ArrayList<FilePatch>()
  val toBeAdded: Collection<FilePath>
    field = ArrayList<FilePath>()
  val toBeDeleted: Collection<FilePath>
    field = ArrayList<FilePath>()

  var ignoreContentRootsCheck: Boolean = false

  // those to be moved to CL: target + created dirs
  val directlyAffected: List<FilePath>
    get() = buildList {
      createdDirectories.mapTo(this) { VcsUtil.getFilePath(it) }
      writableFiles.mapTo(this) { VcsUtil.getFilePath(it) }
      addAll(beforePaths)
    }

  // old parents of moved files
  val allAffected: List<VirtualFile>
    get() = buildList {
      addAll(createdDirectories)
      addAll(writableFiles)
      // after files' parent
      movedFiles.keys.mapNotNullTo(this) { it.parent }
      // before..
      beforePaths.mapNotNullTo(this) { it.parentPath?.virtualFile }
    }

  fun nonWriteActionPreCheck(): List<FilePatch> {
    val failedMessages = ArrayList<@DialogMessage String>()
    val failedToApply = ArrayList<FilePatch>()
    val context = DelayedPrecheckContext(project)
    delayedPrecheckContext = context
    for (patch in patches) {
      val checker = getChecker(patch)
      if (!checker.canBeApplied(context)) {
        checker.errorMessage?.let { failedMessages.add(it) }
        failedToApply.add(patch)
      }
    }
    showErrors(failedMessages)
    val skippedPatches = context.doDelayed()
    skipped.addAll(skippedPatches)
    patches.removeAll(skippedPatches)
    patches.removeAll(failedToApply)
    return failedToApply
  }

  fun execute(): List<FilePatch> {
    val failedMessages = ArrayList<@DialogMessage String>()
    val failedPatches = ArrayList<FilePatch>()
    for (patch in patches) {
      val checker = getChecker(patch)
      if (!checker.check()) {
        checker.errorMessage?.let { failedMessages.add(it) }
        failedPatches.add(checker.patch)
      }
    }
    showErrors(failedMessages)
    patches.removeAll(failedPatches)
    return failedPatches
  }

  private fun showErrors(messages: List<@DialogMessage String>) {
    if (messages.isEmpty()) return
    PatchApplier.showError(project, StringUtil.join(messages, "\n"))
  }

  private fun getChecker(patch: FilePatch): CheckPath {
    val beforeFileName = patch.beforeName
    val afterFileName = patch.afterName
    return when {
      beforeFileName == null || patch.isNewFile -> CheckAdded(patch)
      afterFileName == null || patch.isDeletedFile -> CheckDeleted(patch)
      beforeFileName != afterFileName -> CheckMoved(patch)
      else -> CheckModified(patch)
    }
  }

  fun filterBadFileTypePatches(): Collection<FilePatch> {
    val failedTextPatches = textPatches.filter { !isFileTypeOk(it.file) }
    textPatches.removeAll(failedTextPatches.toSet())
    return failedTextPatches.map { it.applyPatch.patch }
  }

  private fun isFileTypeOk(file: VirtualFile): Boolean {
    if (file.isDirectory) {
      PatchApplier.showError(project, VcsBundle.message("patch.apply.file.type.directory.error", file.presentableName))
      return false
    }
    var fileType = file.fileType
    if (fileType === FileTypes.UNKNOWN) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment) {
        ApplicationManager.getApplication().runWriteAction(Runnable {
          FileTypeManagerEx.getInstanceEx().associate(FileTypes.PLAIN_TEXT, ExactFileNameMatcher(file.name))
        })
        return true
      }
      fileType = FileTypeChooser.associateFileType(file.name) ?: run {
        PatchApplier.showError(project, VcsBundle.message("patch.apply.file.type.undefined.error", file.presentableName))
        return false
      }
    }
    if (fileType.isBinary) {
      PatchApplier.showError(project, VcsBundle.message("patch.apply.file.type.binary.error", file.presentableName))
      return false
    }
    return true
  }

  private inner class CheckModified(patch: FilePatch) : CheckDeleted(patch) {
    override fun precheck(beforeFile: VirtualFile?, afterFile: VirtualFile?, context: DelayedPrecheckContext): Boolean {
      if (beforeFile == null) {
        errorMessage = fileNotFoundMessage(beforeName)
        return false
      }
      return true
    }
  }

  private open inner class CheckDeleted(patch: FilePatch) : CheckPath(patch) {
    override fun precheck(beforeFile: VirtualFile?, afterFile: VirtualFile?, context: DelayedPrecheckContext): Boolean {
      if (beforeFile == null) {
        context.addSkip(getMappedFilePath(beforeName), patch)
      }
      return true
    }

    override fun check(): Boolean {
      val beforeFile = checkExistsAndValid(getMappedFile(beforeName), beforeName) ?: return false
      addPatch(patch, beforeFile)
      val filePath = VcsUtil.getFilePath(beforeFile.parent, beforeFile.name, beforeFile.isDirectory)
      if (patch.isDeletedFile || patch.afterName == null) {
        // See VcsFileListenerContextHelper javadoc
        toBeDeleted.add(filePath)
      }
      beforePaths.add(filePath)
      return true
    }
  }

  private inner class CheckAdded(patch: FilePatch) : CheckPath(patch) {
    override fun precheck(beforeFile: VirtualFile?, afterFile: VirtualFile?, context: DelayedPrecheckContext): Boolean {
      if (checkTargetValid(afterName) == null) {
        return false
      }
      if (afterFile != null) {
        context.addOverrideExisting(patch, VcsUtil.getFilePath(afterFile))
      }
      return true
    }

    override fun check(): Boolean {
      val target = checkTargetValid(afterName) ?: return false
      val parent = try {
        makeSureParentPathExists(target)
      }
      catch (e: IOException) {
        errorMessage = cantCreateFileMessage(afterName, e)
        return false
      }
      if (parent == null) {
        errorMessage = fileNotFoundMessage(afterName)
        return false
      }
      val name = target.name
      val existingFile = parent.findChild(name)
      val afterFilePath = VcsUtil.getFilePath(parent, name, existingFile?.isDirectory ?: false)
      val file = try {
        //if user already accepted overwriting, we shouldn't have created a new one
        if (delayedPrecheckContext.overriddenPaths.contains(afterFilePath)) existingFile
        else createFile(parent, name)
      }
      catch (e: IOException) {
        errorMessage = cantCreateFileMessage(afterName, e)
        return false
      }
      if (file == null) {
        errorMessage = fileNotFoundMessage(afterName)
        return false
      }
      // See VcsFileListenerContextHelper javadoc
      toBeAdded.add(VcsUtil.getFilePath(file))
      if (!checkModificationValid(file, afterName)) {
        return false
      }
      addPatch(patch, file)
      return true
    }
  }

  private inner class CheckMoved(patch: FilePatch) : CheckPath(patch) {
    // before exists; after does not exist
    override fun precheck(beforeFile: VirtualFile?, afterFile: VirtualFile?, context: DelayedPrecheckContext): Boolean {
      if (beforeFile == null) {
        errorMessage = fileNotFoundMessage(beforeName)
        return false
      }
      if (afterFile != null) {
        errorMessage = fileAlreadyExists(afterFile.path)
        return false
      }
      return checkTargetValid(afterName) != null
    }

    override fun check(): Boolean {
      val beforeFile = checkExistsAndValid(getMappedFile(beforeName), beforeName) ?: return false
      val target = checkTargetValid(afterName) ?: return false
      val afterFileParent = try {
        makeSureParentPathExists(target)
      }
      catch (e: IOException) {
        errorMessage = cantCreateFileMessage(afterName, e)
        return false
      }
      if (afterFileParent == null) {
        errorMessage = fileNotFoundMessage(afterName)
        return false
      }
      movedFiles[beforeFile] = MovedFileData(afterFileParent, beforeFile, target.name)
      addPatch(patch, beforeFile)
      return true
    }
  }

  private abstract inner class CheckPath(val patch: FilePatch) {
    protected val beforeName: String? = patch.beforeName
    protected val afterName: String? = patch.afterName
    var errorMessage: @DialogMessage String? = null

    fun canBeApplied(context: DelayedPrecheckContext): Boolean {
      return precheck(getMappedFile(beforeName), getMappedFile(afterName), context)
    }

    protected abstract fun precheck(beforeFile: VirtualFile?, afterFile: VirtualFile?, context: DelayedPrecheckContext): Boolean

    abstract fun check(): Boolean

    /**
     * Returns [file] when it exists and the patch can change it.
     * Otherwise, sets [errorMessage] and returns null.
     */
    protected fun checkExistsAndValid(file: VirtualFile?, name: String?): VirtualFile? {
      if (file == null) {
        errorMessage = fileNotFoundMessage(name)
        return null
      }
      return if (checkModificationValid(file, name)) file else null
    }

    protected fun checkModificationValid(file: VirtualFile, name: String?): Boolean {
      // security check to avoid overwriting system files with a patch
      return checkTrusted(name) { vcsManager.isFileInContent(file) || vcsManager.getVcsRootFor(file) != null }
    }

    /**
     * Checks the target of [path] before the patch changes the disk.
     * The target can be a file that does not exist yet.
     * The content check needs an existing file, so it uses the nearest existing file of the target.
     * The check of the ignored names and of the VCS root uses the target path.
     *
     * @return the target with resolved `..` segments, or null when the check fails
     */
    protected fun checkTargetValid(path: String?): FilePath? {
      val target = path?.let { getMappedFilePath(it) }
      val existingFile = target?.let { findNearestExistingFile(it, ArrayList()) }
      if (target == null || existingFile == null) {
        errorMessage = fileNotFoundMessage(path)
        return null
      }
      val trusted = checkTrusted(path) {
        val inContent = vcsManager.isFileInContent(existingFile) && !vcsManager.isIgnored(target)
        inContent || vcsManager.getVcsRootFor(target) != null
      }
      return if (trusted) target else null
    }

    private fun checkTrusted(name: String?, isTrusted: () -> Boolean): Boolean {
      if (ApplicationManager.getApplication().isUnitTestMode && ignoreContentRootsCheck) return true
      if (!isTrusted()) {
        errorMessage = VcsBundle.message("patch.apply.outside.content.root.message", name)
        return false
      }
      return true
    }

    protected fun getMappedFile(path: String?): VirtualFile? = PathMerger.getFile(baseDirectory, path)

    protected fun getMappedFilePath(path: String?): FilePath? = PathMerger.getFile(VcsUtil.getFilePath(baseDirectory), path)
  }

  private fun addPatch(patch: FilePatch, file: VirtualFile) {
    if (patch is TextFilePatch) {
      textPatches.add(PatchAndFile(file, ApplyFilePatchFactory.create(patch)))
    }
    else {
      binaryPatches.add(PatchAndFile(file, ApplyFilePatchFactory.createGeneral(patch)))
    }
    writableFiles.add(file)
  }

  /**
   * Returns the parent directory of [target], and creates the missing directories of it.
   * The target must have resolved `..` segments, so the new directories are in the target path only.
   */
  @Throws(IOException::class)
  private fun makeSureParentPathExists(target: FilePath): VirtualFile? {
    val parentPath = target.parentPath ?: return null
    val missingNames = ArrayList<String>()
    var child = findNearestExistingFile(parentPath, missingNames) ?: return null
    for (name in missingNames) {
      child = child.findChild(name) ?: child.createChildDirectory(PatchApplier::class.java, name).also { createdDirectories.add(it) }
    }
    return child
  }

  @Throws(IOException::class)
  fun doMoveIfNeeded(file: VirtualFile) {
    val movedFile = movedFiles[file] ?: return
    beforePaths.add(VcsUtil.getFilePath(file))
    ApplicationManager.getApplication().runWriteAction(ThrowableComputable<VirtualFile, IOException> { movedFile.doMove() })
  }

  private class MovedFileData(private val newParent: VirtualFile, private val current: VirtualFile, private val newName: String) {
    @Throws(IOException::class)
    fun doMove(): VirtualFile {
      val oldParent = current.parent
      val needRename = current.name != newName
      val needMove = newParent != oldParent
      if (needRename) {
        if (needMove) {
          val oldParentPath = oldParent!!.toNioPathOrNull()
          if (oldParentPath != null && Files.exists(oldParentPath.resolve(newName)) && current.exists()) {
            // if there is a conflict during first rename we have to rename to third name, then move, then rename to final target
            performRenameWithConflicts(oldParentPath)
            return current
          }
        }
        current.rename(PatchApplier::class.java, newName)
      }
      if (needMove) {
        current.move(PatchApplier::class.java, newParent)
      }
      return current
    }

    @Throws(IOException::class)
    private fun performRenameWithConflicts(oldParent: Path) {
      val newParentPath = newParent.toNioPath()
      var tempName = reserveTempName(oldParent, "tempFileToMove") //NON-NLS
      while (Files.exists(newParentPath.resolve(tempName))) {
        tempName = reserveTempName(oldParent, FileUtilRt.getNameWithoutExtension(tempName))
      }
      current.rename(PatchApplier::class.java, tempName)
      current.move(PatchApplier::class.java, newParent)
      current.rename(PatchApplier::class.java, newName)
    }

    /**
     * Returns a name that no file in [dir] has.
     * The function creates a temporary file with this name and deletes it at once.
     */
    @Throws(IOException::class)
    private fun reserveTempName(dir: Path, prefix: String): String {
      val tempFile = Files.createTempFile(dir, prefix, null)
      Files.delete(tempFile)
      return tempFile.fileName.toString()
    }
  }

  private class DelayedPrecheckContext(private val project: Project) {
    private val skipDeleted = HashMap<FilePath?, FilePatch>()
    private val overrideExisting = HashMap<FilePath, FilePatch>()
    val overriddenPaths = ArrayList<FilePath>()

    fun addSkip(path: FilePath?, filePatch: FilePatch) {
      skipDeleted[path] = filePatch
    }

    fun addOverrideExisting(patch: FilePatch, filePath: FilePath) {
      overrideExisting.putIfAbsent(filePath, patch)
    }

    // returns those to be skipped
    fun doDelayed(): Collection<FilePatch> {
      val result = ArrayList<FilePatch>()
      if (overrideExisting.isNotEmpty()) {
        ApplicationManager.getApplication().invokeAndWait {
          val title = VcsBundle.message("patch.apply.overwrite.existing.title")
          @Suppress("InvalidBundleOrProperty")
          val selected = AbstractVcsHelper.getInstance(project).selectFilePathsToProcess(
            ArrayList(overrideExisting.keys), title, VcsBundle.message("patch.apply.overwrite.existing.files.prompt"), title,
            VcsBundle.message("patch.apply.overwrite.existing.file.prompt"),
            VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION,
            CommonBundle.message("button.overwrite"), IdeBundle.message("button.cancel"))
          if (selected != null) {
            overrideExisting.keys.removeAll(selected.toSet())
            overriddenPaths.addAll(selected)
          }
          result.addAll(overrideExisting.values)
        }
      }
      result.addAll(skipDeleted.values)
      return result
    }
  }

  class PatchAndFile(val file: VirtualFile, val applyPatch: ApplyFilePatchBase<*>)

  private companion object {
    private fun cantCreateFileMessage(path: String?, e: IOException): @DialogMessage String {
      return VcsBundle.message("cannot.create.directory.for.patch", path, e.message)
    }

    private fun fileNotFoundMessage(path: String?): @DialogMessage String {
      return VcsBundle.message("cannot.find.file.to.patch", path)
    }

    private fun fileAlreadyExists(path: String): @DialogMessage String {
      return VcsBundle.message("cannot.apply.file.already.exists", path)
    }

    @Throws(IOException::class)
    private fun createFile(parent: VirtualFile, name: String): VirtualFile {
      return parent.createChildData(PatchApplier::class.java, name)
    }

    /**
     * Returns the file of [path], or of the nearest parent of it that exists.
     * Adds the names of the missing parts of the path to [missingNames], from the top down.
     */
    private fun findNearestExistingFile(path: FilePath, missingNames: MutableList<in String>): VirtualFile? {
      for (current in generateSequence(path) { it.parentPath }) {
        current.virtualFile?.let { return it }
        missingNames.add(0, current.name)
      }
      return null
    }
  }
}
