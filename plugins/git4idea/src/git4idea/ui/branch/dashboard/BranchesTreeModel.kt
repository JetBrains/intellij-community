// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_REPOSITORY
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ThreeState
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.IncomingOutgoingState
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.tree.DefaultMutableTreeNode

internal data class RemoteInfo(val remoteName: String, val repository: GitRepository?)

internal data class BranchInfo(val branch: GitBranch,
                               val isCurrent: Boolean,
                               var isFavorite: Boolean,
                               var incomingOutgoingState: IncomingOutgoingState = IncomingOutgoingState.EMPTY,
                               val repositories: List<GitRepository>) {
  var isMy: ThreeState = ThreeState.UNSURE
  val branchName: @NlsSafe String get() = branch.name
  val isLocalBranch = branch is GitLocalBranch

  override fun toString() = branchName
}

internal sealed class BranchNodeDescriptor {
  open var parent: BranchNodeDescriptor? = null
  abstract val displayName: String?

  internal object Root : BranchNodeDescriptor() {
    override val displayName = null

    override fun toString() = "ROOT"
  }

  internal object Head : BranchNodeDescriptor() {
    override val displayName: @Nls String = message("group.Git.HEAD.Branch.Filter.title")

    override fun toString() = "HEAD"
  }

  internal data class TopLevelGroup(val refType: GitRefType) : BranchNodeDescriptor() {
    override val displayName: @Nls String = refType.getText()

    override fun toString() = refType.name
  }

  internal data class Branch(
    val branchInfo: BranchInfo,
    override var parent: BranchNodeDescriptor?,
    override val displayName: @NlsSafe String = branchInfo.branchName,
  ) : BranchNodeDescriptor() {
    override fun toString(): String = "BRANCH:${branchInfo.branchName}"
  }

  internal data class Repository(val repository: GitRepository, override var parent: BranchNodeDescriptor?) : BranchNodeDescriptor() {
    override val displayName: @NlsSafe String = DvcsUtil.getShortRepositoryName(repository)

    override fun toString(): String = "REPO:$displayName"
  }

  internal data class Group(override val displayName: @NlsSafe String, override var parent: BranchNodeDescriptor?) : BranchNodeDescriptor() {
    override fun toString(): String = "GROUP:$displayName"
  }
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {

  fun getTextRepresentation(): @Nls String =
    (userObject as? BranchNodeDescriptor)?.displayName
           ?: super.toString() //NON-NLS

  fun getNodeDescriptor() = userObject as BranchNodeDescriptor

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (other !is BranchTreeNode) return false

    return Objects.equals(this.userObject, other.userObject)
  }

  override fun hashCode() = Objects.hash(userObject)
}

internal class NodeDescriptorsModel(
  private val localRootNodeDescriptor: BranchNodeDescriptor,
  private val remoteRootNodeDescriptor: BranchNodeDescriptor,
) {
  /**
   * Parent node descriptor to direct children map
   */
  private val branchNodeDescriptors = hashMapOf<BranchNodeDescriptor, MutableSet<BranchNodeDescriptor>>()


  fun getChildrenForParent(parent: BranchNodeDescriptor): Set<BranchNodeDescriptor> =
    branchNodeDescriptors.getOrDefault(parent, emptySet())

  fun reloadFrom(
    localBranches: Collection<BranchInfo>,
    remoteBranches: Collection<BranchInfo>,
    filter: (BranchInfo) -> Boolean,
    groupingConfig: Map<GroupingKey, Boolean>,
  ) {
    clear()

    val branches = (localBranches.asSequence() + remoteBranches.asSequence()).filter(filter)

    branches.forEach { branch -> populateFrom(branch, groupingConfig) }
    branchNodeDescriptors.forEach { (parent, children) ->
      children.forEach { it.parent = parent }
    }
  }

  private fun clear() = branchNodeDescriptors.clear()

  private fun populateFrom(br: BranchInfo, groupingConfig: Map<GroupingKey, Boolean>) {
    val curParent: BranchNodeDescriptor = if (br.isLocalBranch) localRootNodeDescriptor else remoteRootNodeDescriptor
    val groupByDirectory = groupingConfig[GROUPING_BY_DIRECTORY]!!
    val groupByRepository = groupingConfig[GROUPING_BY_REPOSITORY]!!

    when {
      groupByRepository && groupByDirectory -> {
        applyGroupingByRepository(curParent, br) { branch, parent -> applyGroupingByDirectory(parent, branch) }
      }
      groupByRepository -> applyGroupingByRepository(curParent, br)
      groupByDirectory -> applyGroupingByDirectory(curParent, br.copy())
      else -> addChild(curParent, BranchNodeDescriptor.Branch(br.copy(), parent = curParent))
    }
  }

  private fun applyGroupingByRepository(
    curParent: BranchNodeDescriptor,
    br: BranchInfo,
    additionalGrouping: ((BranchInfo, BranchNodeDescriptor) -> Unit)? = null,
  ) {
    val repositoryNodeDescriptors = hashMapOf<GitRepository, BranchNodeDescriptor>()
    br.repositories.forEach { repository ->
      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(repository.project)

      val branch = br.copy(isCurrent = repository.isCurrentBranch(br.branchName),
                           isFavorite = repository.isFavorite(br),
                           incomingOutgoingState = incomingOutgoingManager.getIncomingOutgoingState(repository, GitLocalBranch(br.branchName)))

      val repositoryNodeDescriptor = repositoryNodeDescriptors.computeIfAbsent(repository) {
        val repositoryNodeDescriptor = BranchNodeDescriptor.Repository(repository = repository, parent = curParent)
        addChild(curParent, repositoryNodeDescriptor)
        repositoryNodeDescriptor
      }

      if (additionalGrouping != null) {
        additionalGrouping.invoke(branch, repositoryNodeDescriptor)
      }
      else {
        val branchNodeDescriptor = BranchNodeDescriptor.Branch(branch, parent = repositoryNodeDescriptor)
        addChild(repositoryNodeDescriptor, branchNodeDescriptor)
      }
    }
  }

  private fun applyGroupingByDirectory(parent: BranchNodeDescriptor, branch: BranchInfo) {
    val iter = branch.branchName.split("/").iterator()
    var curParent = parent

    while (iter.hasNext()) {
      @NlsSafe val branchNamePart = iter.next()
      val groupNode = iter.hasNext()
      val branchNodeDescriptor = if (groupNode) {
        BranchNodeDescriptor.Group(parent = curParent, displayName = branchNamePart)
      } else {
        BranchNodeDescriptor.Branch(branch, parent = curParent, displayName = branchNamePart)
      }

      addChild(curParent, branchNodeDescriptor)
      curParent = branchNodeDescriptor
    }
  }

  private fun addChild(parent: BranchNodeDescriptor, child: BranchNodeDescriptor) {
    val directChildren = branchNodeDescriptors.computeIfAbsent(parent) { sortedSetOf(BranchTreeNodeComparator) }
    directChildren.add(child)
    branchNodeDescriptors[parent] = directChildren
  }

  private fun GitRepository.isCurrentBranch(branchName: String) = currentBranch?.name == branchName
  private fun GitRepository.isFavorite(branch: BranchInfo) =
    project.service<GitBranchManager>().isFavorite(if (branch.isLocalBranch) GitBranchType.LOCAL else GitBranchType.REMOTE,
                                                   this, branch.branchName)
}
