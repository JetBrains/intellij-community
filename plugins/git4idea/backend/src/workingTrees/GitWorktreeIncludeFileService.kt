// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.index.NUL
import git4idea.repo.GitRepository
import java.io.IOException
import java.nio.file.Path

internal const val WORKTREE_INCLUDE_FILE_NAME: String = ".worktreeinclude"

/**
 * File I/O for the New Worktree dialog's "Copy project config" section: creating and editing a repository's
 * `.worktreeinclude` file. Kept separate from [git4idea.workingTrees.dialog.GitWorktreeConfigCategoriesPanel],
 * which only builds and wires up the UI.
 */
@Service(Service.Level.PROJECT)
internal class GitWorktreeIncludeFileService {

  companion object {
    private val LOG = logger<GitWorktreeIncludeFileService>()

    /**
     * Marks the idea-settings block this file generates. [hasIdeaSettingsSection] looks for this exact text,
     * so this must not be localized: a locale-dependent marker would make "does this file already have the
     * section" locale-dependent too, and could silently duplicate the section.
     */
    private const val IDEA_SETTINGS_SECTION_MARKER: String = "# --- IntelliJ project settings, added automatically ---"

    /**
     * Name patterns for files that are typically local overrides, never meant to be committed. The list
     * includes [WORKTREE_INCLUDE_FILE_NAME] itself. An untracked .worktreeinclude file then matches its own
     * pattern, so [findWorktreeIncludeMatches] and the copy pipeline propagate it into a freshly created
     * worktree too.
     */
    private val LOCAL_OVERRIDE_NAME_PATTERNS = listOf(
      WORKTREE_INCLUDE_FILE_NAME,
      ".env", ".env.local", ".env.*.local",
      "local.properties", "*.local.properties",
      "docker-compose.override.yml", "docker-compose.override.yaml",
      "*.local.yml", "*.local.yaml", "*.local.json", "*.local.xml",
      "secrets.yml", "secrets.yaml",
    )

    private val IDEA_SETTINGS_NAME_PATTERNS = listOf(
      ".idea/*.xml",
      ".idea/runConfigurations/",
      ".idea/codeStyles/",
      ".idea/inspectionProfiles/",
      ".run/",
    )

    fun getInstance(project: Project): GitWorktreeIncludeFileService =
      project.getService(GitWorktreeIncludeFileService::class.java)

    /**
     * Returns every path under [root] that matches a pattern in [worktreeIncludeFile], tracked or not. This
     * reuses git's own gitignore-syntax engine instead of a hand-rolled matcher, so a negated pattern (`!foo`)
     * behaves exactly like it does in a real `.gitignore`.
     */
    internal fun findWorktreeIncludeMatches(project: Project, root: VirtualFile, worktreeIncludeFile: Path): List<Path> {
      require(worktreeIncludeFile == root.toNioPath().resolve(WORKTREE_INCLUDE_FILE_NAME)) {
        "worktreeIncludeFile must be $WORKTREE_INCLUDE_FILE_NAME directly under root, got $worktreeIncludeFile"
      }

      val handler = GitLineHandler(project, root, GitCommand.LS_FILES)
      handler.setSilent(true)
      // --exclude-from takes a plain command-line argument, which is not converted for a remote Eel environment
      // the way the handler's own executable and working directory are. Passing the file name relative to that
      // working directory (already `root`) avoids the need for a host-to-target path conversion here.
      handler.addParameters("-z", "--cached", "--others", "--ignored", "--exclude-from=$WORKTREE_INCLUDE_FILE_NAME")
      handler.endOptions()

      val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
      if (output.isBlank()) return emptyList()

      val rootPath = root.toNioPath()
      return output.split(NUL).filter { it.isNotEmpty() }.map { rootPath.resolve(it) }
    }

    internal fun generateWorktreeIncludeContent(includeIdeaSettings: Boolean): String {
      val content = GitBundle.message("working.tree.dialog.worktree.include.header.comment") + "\n" +
        LOCAL_OVERRIDE_NAME_PATTERNS.joinToString("\n") + "\n"
      return if (includeIdeaSettings) content + ideaSettingsSection() else content
    }

    /** Returns `true` when [content] already has the idea-settings block [generateWorktreeIncludeContent] adds. */
    internal fun hasIdeaSettingsSection(content: String): Boolean = content.contains(IDEA_SETTINGS_SECTION_MARKER)

    /** Appends the idea-settings block to [content], unless [hasIdeaSettingsSection] is already `true`. */
    internal fun appendIdeaSettingsSection(content: String): String {
      if (hasIdeaSettingsSection(content)) return content
      val separator = if (content.isEmpty() || content.endsWith("\n")) "" else "\n"
      return content + separator + ideaSettingsSection()
    }

    private fun ideaSettingsSection(): String =
      "$IDEA_SETTINGS_SECTION_MARKER\n" +
      GitBundle.message("working.tree.dialog.worktree.include.idea.settings.comment") + "\n" +
      IDEA_SETTINGS_NAME_PATTERNS.joinToString("\n") + "\n"
  }

  /**
   * Writes a new `.worktreeinclude` file with [content] under [repository]'s root. Returns `true` on success;
   * the file always lands at the fixed [WORKTREE_INCLUDE_FILE_NAME] path under [repository]'s root, so the
   * caller already knows it.
   */
  fun writeWorktreeIncludeFile(repository: GitRepository, content: String): Boolean {
    val root = repository.root
    return try {
      runWriteAction {
        val newFile = root.createChildData(root, WORKTREE_INCLUDE_FILE_NAME)
        VfsUtil.saveText(newFile, content)
      }
      true
    }
    catch (e: IOException) {
      LOG.warn("Failed to create $WORKTREE_INCLUDE_FILE_NAME under ${root.path}", e)
      false
    }
  }

  /**
   * Appends the idea-settings block to [repository]'s existing `.worktreeinclude` file. Returns `true` on
   * success, and `false` both when the file does not exist and when the update fails.
   */
  fun addIdeaSettingsSection(repository: GitRepository): Boolean {
    val root = repository.root
    val file = root.findChild(WORKTREE_INCLUDE_FILE_NAME) ?: return false
    return try {
      val updatedContent = appendIdeaSettingsSection(VfsUtil.loadText(file))
      runWriteAction {
        VfsUtil.saveText(file, updatedContent)
      }
      true
    }
    catch (e: IOException) {
      LOG.warn("Failed to update $WORKTREE_INCLUDE_FILE_NAME under ${root.path}", e)
      false
    }
  }
}
