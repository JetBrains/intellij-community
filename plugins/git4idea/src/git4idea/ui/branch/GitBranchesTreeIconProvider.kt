package git4idea.ui.branch

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getRepositoryIcon
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformIcons
import git4idea.GitReference
import git4idea.GitTag
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import javax.swing.Icon

internal class GitBranchesTreeIconProvider(project: Project) {
  private val colorManager = RepositoryChangesBrowserNode.getColorManager(project)

  fun forRef(gitReference: GitReference, current: Boolean, favorite: Boolean, selected: Boolean, favoriteToggleOnClick: Boolean = false): Icon = when {
    selected && !favorite && favoriteToggleOnClick -> AllIcons.Nodes.NotFavoriteOnHover
    current && favorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
    current -> DvcsImplIcons.CurrentBranchLabel
    favorite -> AllIcons.Nodes.Favorite
    gitReference is GitTag -> DvcsImplIcons.BranchLabel
    else -> AllIcons.Vcs.BranchNode
  }

  fun forRepository(repository: GitRepository) = getRepositoryIcon(repository, colorManager)

  fun forGroup() = PlatformIcons.FOLDER_ICON
}