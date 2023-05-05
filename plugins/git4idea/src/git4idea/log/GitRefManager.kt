// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.dvcs.repo.RepositoryManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.*
import com.intellij.vcs.log.impl.SimpleRefGroup
import com.intellij.vcs.log.impl.SimpleRefGroup.Companion.buildGroups
import com.intellij.vcs.log.impl.SimpleRefType
import com.intellij.vcs.log.impl.SingletonRefGroup
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitBranch
import git4idea.GitTag
import git4idea.branch.GitBranchType
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.util.*

/**
 * @author Kirill Likhodedov
 */
class GitRefManager(project: Project, private val repositoryManager: RepositoryManager<GitRepository>) : VcsLogRefManager {
  private val labelsComparator: Comparator<VcsRef> = GitLabelComparator(repositoryManager)
  private val branchLayoutComparator: Comparator<VcsRef> = GitBranchLayoutComparator()
  private val branchManager: GitBranchManager = project.getService(GitBranchManager::class.java)

  override fun getLabelsOrderComparator() = labelsComparator

  override fun getBranchLayoutComparator() = branchLayoutComparator

  override fun groupForBranchFilter(refs: Collection<VcsRef>): List<RefGroup> {
    val simpleGroups = ArrayList<RefGroup>()
    val localBranches = ArrayList<VcsRef>()
    val remoteRefGroups = MultiMap.create<GitRemote, VcsRef>()

    val refsByRoot = groupRefsByRoot(refs)
    for ((root, value) in refsByRoot.entrySet()) {
      val refsInRoot = ContainerUtil.sorted(value, labelsComparator)

      val repository = repositoryManager.getRepositoryForRootQuick(root)
      if (repository == null) {
        LOG.warn("No repository for root: $root")
        continue
      }

      val locals = getLocalBranches(repository)
      val allRemote = getAllRemoteBranches(repository)

      for (ref in refsInRoot) {
        if (ref.type === HEAD) {
          simpleGroups.add(SingletonRefGroup(ref))
          continue
        }

        val refName = ref.name
        if (locals.contains(refName)) {
          localBranches.add(ref)
        }
        else if (allRemote.containsKey(refName)) {
          remoteRefGroups.putValue(allRemote[refName], ref)
        }
        else {
          LOG.debug("Didn't find ref neither in local nor in remote branches: $ref")
        }
      }
    }

    val result = ArrayList(simpleGroups)
    if (!localBranches.isEmpty()) result.add(SimpleRefGroup(GitBundle.message("git.log.refGroup.local"), localBranches, false))
    for ((key, value) in remoteRefGroups.entrySet()) {
      result.add(RemoteRefGroup(key, value))
    }
    return result
  }

  override fun groupForTable(references: Collection<VcsRef>, compact: Boolean, showTagNames: Boolean): List<RefGroup> {
    if (references.isEmpty()) return emptyList()

    val sortedReferences = ContainerUtil.sorted(references, labelsComparator)
    val groupedRefs = ContainerUtil.groupBy(sortedReferences) { it.type }

    val headRefs = groupedRefs.remove(HEAD)

    val repository = getRepository(references)

    val trackedRefs = repository?.getTrackedRefs(groupedRefs) ?: emptyList()
    trackedRefs.forEach { refGroup: RefGroup ->
      groupedRefs.remove(LOCAL_BRANCH, refGroup.refs[0])
      groupedRefs.remove(REMOTE_BRANCH, refGroup.refs[1])
    }

    val currentBranch = repository?.currentBranchName?.let { branchName ->
      val branch = groupedRefs[LOCAL_BRANCH].firstOrNull { it.name == branchName } ?: return@let null
      groupedRefs[LOCAL_BRANCH].remove(branch)
      SimpleRefGroup(branchName, mutableListOf(branch))
    }

    val refGroups = buildGroups(listOfNotNull(currentBranch) + trackedRefs, groupedRefs, compact, showTagNames)
    if (headRefs.isNullOrEmpty()) return refGroups

    val result = ArrayList<RefGroup>()
    result.addAll(refGroups)
    if (repository != null && !repository.isOnBranch) {
      result.add(0, SimpleRefGroup("!", headRefs.toMutableList()))
    }
    else {
      if (!result.isEmpty()) {
        result.first().refs.addAll(0, headRefs.toMutableList())
      }
      else {
        result.add(0, SimpleRefGroup("", headRefs.toMutableList()))
      }
    }
    return result
  }

  private fun getRepository(references: Collection<VcsRef>): GitRepository? {
    if (references.isEmpty()) return null

    val ref = references.first()
    val repository = getRepository(ref)
    if (repository == null) {
      LOG.warn("No repository for root: " + ref.root)
    }
    return repository
  }

  @Throws(IOException::class)
  override fun serialize(out: DataOutput, type: VcsRefType) {
    out.writeInt(REF_TYPE_INDEX.indexOf(type))
  }

  @Throws(IOException::class)
  override fun deserialize(`in`: DataInput): VcsRefType {
    val id = `in`.readInt()
    if (id < 0 || id > REF_TYPE_INDEX.size - 1) throw IOException("Reference type by id $id does not exist")
    return REF_TYPE_INDEX[id]
  }

  private fun getRepository(reference: VcsRef): GitRepository? {
    return repositoryManager.getRepositoryForRootQuick(reference.root)
  }

  override fun isFavorite(reference: VcsRef): Boolean {
    if (reference.type == HEAD) return true
    if (!reference.type.isBranch) return false
    return branchManager.isFavorite(getBranchType(reference), getRepository(reference), reference.name)
  }

  override fun setFavorite(reference: VcsRef, favorite: Boolean) {
    if (reference.type == HEAD) return
    if (!reference.type.isBranch) return
    branchManager.setFavorite(getBranchType(reference), getRepository(reference), reference.name, favorite)
  }

  private enum class RefType {
    OTHER,
    HEAD,
    CURRENT_BRANCH,
    TAG,
    LOCAL_BRANCH,
    MASTER,
    REMOTE_BRANCH,
    ORIGIN_MASTER
  }

  private inner class RemoteRefGroup(private val remote: GitRemote, private val branches: Collection<VcsRef>) : RefGroup {
    override fun isExpanded() = false
    override fun getName() = remote.name + "/..."
    override fun getRefs() = ContainerUtil.sorted(branches, labelsOrderComparator)
    override fun getColors() = listOf(VcsLogStandardColors.Refs.BRANCH_REF)
  }

  private class GitLabelComparator(private val repositoryManager: RepositoryManager<GitRepository>) : GitRefComparator() {
    override val orderedTypes = arrayOf(
      RefType.HEAD,
      RefType.CURRENT_BRANCH,
      RefType.MASTER,
      RefType.ORIGIN_MASTER,
      RefType.LOCAL_BRANCH,
      RefType.REMOTE_BRANCH,
      RefType.TAG,
      RefType.OTHER
    )

    override fun getType(ref: VcsRef): RefType {
      val type = super.getType(ref)
      if (type == RefType.LOCAL_BRANCH || type == RefType.MASTER) {
        if (isCurrentBranch(ref)) {
          return RefType.CURRENT_BRANCH
        }
      }
      return type
    }

    private fun isCurrentBranch(ref: VcsRef): Boolean {
      val repo = repositoryManager.getRepositoryForRootQuick(ref.root) ?: return false
      val currentBranch = repo.currentBranch ?: return false
      return currentBranch.name == ref.name
    }
  }

  private class GitBranchLayoutComparator : GitRefComparator() {
    override val orderedTypes: Array<RefType> = arrayOf(
      RefType.ORIGIN_MASTER,
      RefType.REMOTE_BRANCH,
      RefType.MASTER,
      RefType.LOCAL_BRANCH,
      RefType.TAG,
      RefType.CURRENT_BRANCH,
      RefType.HEAD,
      RefType.OTHER
    )
  }

  private abstract class GitRefComparator : Comparator<VcsRef> {
    protected abstract val orderedTypes: Array<RefType>

    override fun compare(ref1: VcsRef, ref2: VcsRef): Int {
      val power1 = ArrayUtil.find(orderedTypes, getType(ref1))
      val power2 = ArrayUtil.find(orderedTypes, getType(ref2))
      if (power1 != power2) {
        return power1 - power2
      }
      val namesComparison = ref1.name.compareTo(ref2.name)
      return if (namesComparison != 0) {
        namesComparison
      }
      else VcsLogUtil.compareRoots(ref1.root, ref2.root)
    }

    protected open fun getType(ref: VcsRef): RefType {
      val type = ref.type
      val name = ref.name
      return when {
        type === HEAD -> RefType.HEAD
        type === TAG -> RefType.TAG
        type === LOCAL_BRANCH -> if (name == MASTER || name == MAIN) RefType.MASTER else RefType.LOCAL_BRANCH
        type === REMOTE_BRANCH -> if (name == ORIGIN_MASTER || name == ORIGIN_MAIN) RefType.ORIGIN_MASTER else RefType.REMOTE_BRANCH
        else -> RefType.OTHER
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitRefManager::class.java)

    private val HEAD_COLOR = JBColor.namedColor("VersionControl.GitLog.headIconColor", VcsLogStandardColors.Refs.TIP)
    private val LOCAL_BRANCH_COLOR = JBColor.namedColor("VersionControl.GitLog.localBranchIconColor", VcsLogStandardColors.Refs.BRANCH)
    private val REMOTE_BRANCH_COLOR = JBColor.namedColor("VersionControl.GitLog.remoteBranchIconColor",
                                                         VcsLogStandardColors.Refs.BRANCH_REF)
    private val TAG_COLOR = JBColor.namedColor("VersionControl.GitLog.tagIconColor", VcsLogStandardColors.Refs.TAG)
    private val OTHER_COLOR = JBColor.namedColor("VersionControl.GitLog.otherIconColor", VcsLogStandardColors.Refs.TAG)

    @JvmField
    val HEAD: VcsRefType = SimpleRefType("HEAD", true, HEAD_COLOR)

    @JvmField
    val LOCAL_BRANCH: VcsRefType = SimpleRefType("LOCAL_BRANCH", true, LOCAL_BRANCH_COLOR)

    @JvmField
    val REMOTE_BRANCH: VcsRefType = SimpleRefType("REMOTE_BRANCH", true, REMOTE_BRANCH_COLOR)

    @JvmField
    val TAG: VcsRefType = SimpleRefType("TAG", false, TAG_COLOR)

    @JvmField
    val OTHER: VcsRefType = SimpleRefType("OTHER", false, OTHER_COLOR)

    private val REF_TYPE_INDEX = listOf(HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG, OTHER)

    const val MASTER = "master"
    const val MAIN = "main"
    const val ORIGIN_MASTER = "origin/master"
    const val ORIGIN_MAIN = "origin/main"

    private const val REMOTE_TABLE_SEPARATOR = " & "
    private const val SEPARATOR = "/"

    private fun GitRepository.getTrackedRefs(groupedRefs: MultiMap<VcsRefType, VcsRef>): List<RefGroup> {
      val result = ArrayList<RefGroup>()

      val locals = groupedRefs[LOCAL_BRANCH]
      val remotes = groupedRefs[REMOTE_BRANCH]

      for (localRef in locals) {
        val group = createTrackedGroup(remotes, localRef)
        if (group != null) {
          result.add(group)
        }
      }

      return result
    }

    private fun GitRepository.createTrackedGroup(references: Collection<VcsRef>, localRef: VcsRef): SimpleRefGroup? {
      val remoteBranches = references.filter { ref -> ref.type == REMOTE_BRANCH }
      val trackInfo = branchTrackInfos.find { info -> info.localBranch.name == localRef.name }
      if (trackInfo != null) {
        val trackedRef = remoteBranches.find { ref -> ref.name == trackInfo.remoteBranch.name }
        if (trackedRef != null) {
          return SimpleRefGroup(trackInfo.remote.name + REMOTE_TABLE_SEPARATOR + localRef.name,
                                mutableListOf(localRef, trackedRef))
        }
      }
      val trackingCandidates = remoteBranches.filter { ref -> ref.name.endsWith(SEPARATOR + localRef.name) }
      for (remote in remotes) {
        for (candidate in trackingCandidates) {
          if (candidate.name == remote.name + SEPARATOR + localRef.name) {
            return SimpleRefGroup(remote.name + REMOTE_TABLE_SEPARATOR + localRef.name,
                                  mutableListOf(localRef, candidate))
          }
        }
      }
      return null
    }

    private fun getBranchType(reference: VcsRef): GitBranchType {
      return if (reference.type == LOCAL_BRANCH) GitBranchType.LOCAL else GitBranchType.REMOTE
    }

    private fun getLocalBranches(repository: GitRepository): Set<String> {
      return ContainerUtil.map2Set(repository.branches.localBranches,
                                   Function { branch: GitBranch -> branch.name } as Function<GitBranch, String>)
    }

    private fun getAllRemoteBranches(repository: GitRepository): Map<String, GitRemote> {
      val all = HashSet(repository.branches.remoteBranches)
      val allRemote = HashMap<String, GitRemote>()
      for (remoteBranch in all) {
        allRemote[remoteBranch.name] = remoteBranch.remote
      }
      return allRemote
    }

    private fun groupRefsByRoot(refs: Iterable<VcsRef>): MultiMap<VirtualFile, VcsRef> {
      val grouped = MultiMap.create<VirtualFile, VcsRef>()
      for (ref in refs) {
        grouped.putValue(ref.root, ref)
      }
      return grouped
    }

    @JvmStatic
    fun getRefType(refName: String): VcsRefType {
      if (refName.startsWith(GitBranch.REFS_HEADS_PREFIX)) return LOCAL_BRANCH
      if (refName.startsWith(GitBranch.REFS_REMOTES_PREFIX)) return REMOTE_BRANCH
      if (refName.startsWith(GitTag.REFS_TAGS_PREFIX)) return TAG
      if (refName.startsWith("HEAD")) return HEAD
      return OTHER
    }
  }
}
