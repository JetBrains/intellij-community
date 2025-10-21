// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.EventDispatcher
import com.intellij.util.ThreeState
import com.intellij.vcs.git.branch.GitInOutCountersInProject
import com.intellij.vcs.git.ui.getText
import git4idea.*
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitRefType
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.tree.DefaultMutableTreeNode

internal data class RemoteInfo(val remoteName: String, val repository: GitRepository?)

internal sealed interface RefInfo {
  var isFavorite: Boolean
  val isCurrent: Boolean
  val repositories: List<GitRepository>

  val ref: GitReference
  val refName: String
    get() = ref.name
}

internal data class BranchInfo(
  val branch: GitBranch,
  override val isCurrent: Boolean,
  override var isFavorite: Boolean,
  var incomingOutgoingState: GitInOutCountersInProject = GitInOutCountersInProject.EMPTY,
  override val repositories: List<GitRepository>,
) : RefInfo {
  var isMy: ThreeState = ThreeState.UNSURE
  val branchName: @NlsSafe String get() = branch.name
  val isLocalBranch = branch is GitLocalBranch

  override val ref = branch

  override fun toString() = branchName
}

internal data class TagInfo(
  val tag: GitTag,
  override val isCurrent: Boolean,
  override var isFavorite: Boolean,
  override val repositories: List<GitRepository>,
) : RefInfo {
  override val ref = tag

  override fun toString() = tag.name
}

@ApiStatus.Internal
sealed class BranchNodeDescriptor {
  abstract val children: List<BranchNodeDescriptor>
  abstract val displayName: String

  internal class Root : BranchNodeDescriptor() {
    override val displayName = "Root"
    override var children: List<BranchNodeDescriptor> = emptyList()

    override fun toString() = "ROOT"
  }

  internal object Head : BranchNodeDescriptor() {
    override val displayName: @Nls String = message("group.Git.HEAD.Branch.Filter.title")
    override val children: List<BranchNodeDescriptor>
      get() = emptyList()

    override fun toString() = "HEAD"
  }

  internal class TopLevelGroup(
    val refType: GitRefType,
    override val children: List<BranchNodeDescriptor>,
  ) : BranchNodeDescriptor() {
    override val displayName: @Nls String = refType.getText()

    override fun toString() = refType.name
  }

  internal class RemoteGroup(val remote: GitRemote, override val children: List<BranchNodeDescriptor>) : BranchNodeDescriptor() {
    override val displayName: String = remote.name

    override fun toString(): String = "REMOTE:$displayName"
  }

  internal sealed class Ref(val refInfo: RefInfo) : BranchNodeDescriptor() {
    override val children: List<BranchNodeDescriptor>
      get() = emptyList()
  }

  internal class Branch(
    val branchInfo: BranchInfo,
    override val displayName: @NlsSafe String = branchInfo.branchName,
  ) : Ref(branchInfo) {
    override fun toString(): String = "BRANCH:${branchInfo.branchName}"
  }

  internal class Tag(
    val tagInfo: TagInfo,
    override val displayName: @NlsSafe String = tagInfo.tag.name,
  ) : Ref(tagInfo) {
    override fun toString(): String = "TAG:${tagInfo.tag.name}"
  }

  internal data class Repository(val repository: GitRepository, override val children: List<BranchNodeDescriptor>) : BranchNodeDescriptor() {
    override val displayName: @NlsSafe String = DvcsUtil.getShortRepositoryName(repository)

    override fun toString(): String = "REPO:$displayName"
  }

  internal class Group(
    override val displayName: @NlsSafe String,
    override val children: List<BranchNodeDescriptor>,
    val hasFavorites: Boolean,
  ) : BranchNodeDescriptor() {
    override fun toString(): String = "GROUP:$displayName"
  }
}

internal class BranchTreeNode(nodeDescriptor: BranchNodeDescriptor) : DefaultMutableTreeNode(nodeDescriptor) {
  override fun getParent(): BranchTreeNode? = super.parent as BranchTreeNode?

  fun getNodeDescriptor() = userObject as BranchNodeDescriptor

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (other !is BranchTreeNode) return false

    return Objects.equals(this.userObject, other.userObject)
  }

  override fun hashCode() = Objects.hash(userObject)
}

@ApiStatus.Internal
interface BranchesTreeModel {
  val root: BranchNodeDescriptor
  val groupingConfig: Map<GroupingKey, Boolean>
  val isLoading: Boolean

  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)

  interface Listener : EventListener {
    fun onTreeChange() {}
    fun onLoadingStateChange() {}
    fun onTreeDataChange() {}
  }
}

@ApiStatus.Internal
abstract class BranchesTreeModelBase : BranchesTreeModel {
  private val _root = BranchNodeDescriptor.Root()
  final override val root: BranchNodeDescriptor = _root
  private val listeners = EventDispatcher.create(BranchesTreeModel.Listener::class.java)

  private val loadingCounter = AtomicInteger()
  final override val isLoading: Boolean
    get() = loadingCounter.get() > 0

  protected fun setTree(nodes: List<BranchNodeDescriptor>) {
    _root.children = nodes
    listeners.multicaster.onTreeChange()
  }

  protected fun startLoading() {
    loadingCounter.incrementAndGet()
    listeners.multicaster.onLoadingStateChange()
  }

  protected fun finishLoading() {
    loadingCounter.decrementAndGet()
    listeners.multicaster.onLoadingStateChange()
  }

  final override fun addListener(listener: BranchesTreeModel.Listener) {
    listeners.addListener(listener)
  }

  final override fun removeListener(listener: BranchesTreeModel.Listener) {
    listeners.removeListener(listener)
  }

  fun onTreeDataChange() {
    listeners.multicaster.onTreeDataChange()
  }
}

@VisibleForTesting
internal object NodeDescriptorsModel {
  fun buildTreeNodes(project: Project, refs: RefsCollection, filter: (RefInfo) -> Boolean, groupingConfig: Map<GroupingKey, Boolean>): List<BranchNodeDescriptor> {
    val groupByRepository = groupingConfig[GroupingKey.GROUPING_BY_REPOSITORY]!!
    val groupByPrefix = groupingConfig[GroupingKey.GROUPING_BY_DIRECTORY]!!

    val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
    val topLevelGroups = mutableListOf<BranchNodeDescriptor>()
    topLevelGroups += BranchNodeDescriptor.Head
    refs.forEach { refs, group ->
      val children = groupByRepoAndPrefixIfApplicable(incomingOutgoingManager, refs.asSequence().filter(filter), groupByRepository, groupByPrefix)
      if (children.isNotEmpty()) {
        topLevelGroups += BranchNodeDescriptor.TopLevelGroup(group, children)
      }
    }
    return topLevelGroups
  }

  private fun groupByRepoAndPrefixIfApplicable(
    incomingOutgoingManager: GitBranchIncomingOutgoingManager,
    refsInfo: Sequence<RefInfo>,
    groupByRepository: Boolean,
    groupByPrefix: Boolean,
  ): List<BranchNodeDescriptor> = if (groupByRepository) {
    val repoToRefs = mutableMapOf<GitRepository, MutableList<RefInfo>>()
    for (refInfo in refsInfo) {
      for (repository in refInfo.repositories) {
        val isFavorite = repository.isFavorite(refInfo)
        val ref = when (refInfo) {
          is BranchInfo -> {
            val incomingOutgoingState =
              if (refInfo.ref is GitLocalBranch) incomingOutgoingManager.getIncomingOutgoingState(repository, refInfo.ref)
              else GitInOutCountersInProject.EMPTY
            refInfo.copy(isCurrent = repository.isCurrentBranch(refInfo.branchName), isFavorite = isFavorite, incomingOutgoingState = incomingOutgoingState)
          }
          is TagInfo -> {
            refInfo.copy(isCurrent = repository.isCurrentTag(refInfo.tag), isFavorite = isFavorite)
          }
        }

        repoToRefs.computeIfAbsent(repository) { mutableListOf() }.add(ref)
      }
    }

    val repoNodes = repoToRefs.map { (repository, repoRefs) ->
      val repoChildren = groupByPrefixAndRemoteIfApplicable(repoRefs, groupByPrefix)
      BranchNodeDescriptor.Repository(repository, repoChildren)
    }

    repoNodes.sortedWith(BranchTreeNodeComparator)
  }
  else groupByPrefixAndRemoteIfApplicable(refsInfo.asIterable(), groupByPrefix)

  private fun groupByPrefixAndRemoteIfApplicable(
    refsInfo: Iterable<RefInfo>,
    groupByPrefix: Boolean,
  ): List<BranchNodeDescriptor> {
    val branchesByRemote = mutableMapOf<GitRemote, MutableList<BranchInfo>>()
    val refsWithoutRemote = mutableListOf<RefInfo>()

    for (refInfo in refsInfo) {
      val remote = ((refInfo as? BranchInfo)?.branch as? GitRemoteBranch)?.remote
      if (!groupByPrefix || remote == null) {
        refsWithoutRemote += refInfo
      }
      else {
        branchesByRemote.computeIfAbsent(remote) { mutableListOf() }.add(refInfo)
      }
    }

    val result = mutableListOf<BranchNodeDescriptor>()
    result += groupByPrefixIfApplicable(refsWithoutRemote, groupByPrefix)
    result += branchesByRemote.map { (remote, remoteBranches) ->
      val remoteChildren = groupByPrefixIfApplicable(remoteBranches, groupByPrefix)
      BranchNodeDescriptor.RemoteGroup(remote, remoteChildren)
    }

    return result.sortedWith(BranchTreeNodeComparator)
  }

  private fun groupByPrefixIfApplicable(refsInfo: Iterable<RefInfo>, groupByPrefix: Boolean): List<BranchNodeDescriptor> =
    if (groupByPrefix) groupByPrefix(refsInfo.map { RefNameSegment(it) })
    else refsInfo.map { it.toNodeDescriptor() }.sortedWith(BranchTreeNodeComparator)

  private fun groupByPrefix(paths: Iterable<RefNameSegment>): List<BranchNodeDescriptor> {
    val nodes = mutableListOf<BranchNodeDescriptor>()
    val childGroups = mutableMapOf<String, MutableList<RefNameSegment>>()

    for (path in paths) {
      val currentSegment = path.currentSegment()
      if (path.isLastSegment()) {
        nodes.add(path.refInfo.toNodeDescriptor(displayName = currentSegment))
      }
      else {
        childGroups.computeIfAbsent(currentSegment) { mutableListOf() }.add(path.apply { move() })
      }
    }

    for ((groupName, childrenPaths) in childGroups) {
      val childrenNodes = groupByPrefix(childrenPaths)
      val hasFavorites = childrenNodes.any { node ->
        node is BranchNodeDescriptor.Ref && node.refInfo.isFavorite || node is BranchNodeDescriptor.Group && node.hasFavorites
      }
      nodes.add(BranchNodeDescriptor.Group(groupName, childrenNodes, hasFavorites))
    }

    return nodes.sortedWith(BranchTreeNodeComparator)
  }

  private class RefNameSegment(val refInfo: RefInfo, private var offset: Int = 0) {
    private val path: List<String>

    init {
      val name = if (refInfo is BranchInfo && refInfo.branch is GitRemoteBranch) refInfo.branch.nameForRemoteOperations else refInfo.refName
      path = name.split("/")
    }

    fun isLastSegment() = offset == path.lastIndex
    fun currentSegment() = path[offset]
    fun move() {
      offset++
    }
  }

  private fun RefInfo.toNodeDescriptor(displayName: String? = null) =
    if (displayName == null) when (this) {
      is BranchInfo -> BranchNodeDescriptor.Branch(this)
      is TagInfo -> BranchNodeDescriptor.Tag(this)
    }
    else when (this) {
      is BranchInfo -> BranchNodeDescriptor.Branch(this, displayName = displayName)
      is TagInfo -> BranchNodeDescriptor.Tag(this, displayName = displayName)
    }

  private fun GitRepository.isCurrentTag(tag: GitTag) = state == Repository.State.DETACHED && GitRefUtil.getCurrentReference(this) == tag
  private fun GitRepository.isCurrentBranch(branchName: String) = currentBranch?.name == branchName
  private fun GitRepository.isFavorite(refInfo: RefInfo) =
    project.service<GitBranchManager>().isFavorite(GitRefType.of(refInfo.ref), this, refInfo.refName)
}
