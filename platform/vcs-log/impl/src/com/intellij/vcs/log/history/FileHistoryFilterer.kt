/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsCachingHistory
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils.assertNotNull
import com.intellij.util.ObjectUtils.notNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.ContainerUtil.getFirstItem
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl
import com.intellij.vcs.log.impl.VcsLogRevisionFilterImpl
import com.intellij.vcs.log.util.StopWatch
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.CommitCountStage
import com.intellij.vcs.log.visible.VcsLogFilterer
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcsUtil.VcsUtil

internal class FileHistoryFilterer(logData: VcsLogData) : VcsLogFilterer {
  private val project = logData.project
  private val logProviders = logData.logProviders
  private val storage = logData.storage
  private val index = logData.index
  private val indexDataGetter = index.dataGetter!!
  private val vcsLogFilterer = VcsLogFiltererImpl(logProviders, storage, logData.topCommitsCache,
                                                  logData.commitDetailsGetter, index)

  override fun filter(dataPack: DataPack,
                      sortType: PermanentGraph.SortType,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val filePath = getFilePath(filters) ?: return vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
    val root = notNull(VcsUtil.getVcsRootFor(project, filePath))
    return MyWorker(root, filePath, getHash(filters)).filter(dataPack, sortType, filters, commitCount)
  }

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean {
    val filePath = getFilePath(filters)
    return filePath != null && !filePath.isDirectory
  }

  private inner class MyWorker constructor(private val root: VirtualFile,
                                           private val filePath: FilePath,
                                           private val hash: Hash?) {

    fun filter(dataPack: DataPack,
               sortType: PermanentGraph.SortType,
               filters: VcsLogFilterCollection,
               commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
      val start = System.currentTimeMillis()

      if (index.isIndexed(root) && (dataPack.isFull || filePath.isDirectory)) {
        val visiblePack = filterWithIndex(dataPack, sortType, filters)
        LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for " + filePath + " with index")
        checkNotEmpty(dataPack, visiblePack, true)
        return Pair.create(visiblePack, commitCount)
      }

      if (filePath.isDirectory) {
        return vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
      }

      val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(root)
      if (vcs != null && vcs.vcsHistoryProvider != null) {
        return try {
          val visiblePack = filterWithProvider(vcs, dataPack, sortType, filters)
          LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                    " for computing history for " +
                    filePath +
                    " with history provider")
          checkNotEmpty(dataPack, visiblePack, false)
          Pair.create(visiblePack, commitCount)
        }
        catch (e: VcsException) {
          LOG.error(e)
          vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
        }

      }

      LOG.warn("Could not find vcs or history provider for file $filePath")
      return vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
    }

    private fun checkNotEmpty(dataPack: DataPack, visiblePack: VisiblePack, withIndex: Boolean) {
      if (!dataPack.isFull) {
        LOG.debug("Data pack is not full while computing file history for " + filePath + "\n" +
                  "Found " + visiblePack.visibleGraph.visibleCommitCount + " commits")
      }
      else if (visiblePack.visibleGraph.visibleCommitCount == 0) {
        LOG.warn("Empty file history from " + (if (withIndex) "index" else "provider") + " for " + filePath)
      }
    }

    @Throws(VcsException::class)
    private fun filterWithProvider(vcs: AbstractVcs<*>,
                                   dataPack: DataPack,
                                   sortType: PermanentGraph.SortType,
                                   filters: VcsLogFilterCollection): VisiblePack {
      var dataPack = dataPack
      val revisionNumber = if (hash != null) VcsLogUtil.convertToRevisionNumber(hash) else null
      val revisions = VcsCachingHistory.collect(vcs, filePath, revisionNumber)

      if (revisions.isEmpty()) return VisiblePack.EMPTY

      val pathsMap = ContainerUtil.newHashMap<Int, FilePath>()
      val visibleGraph: VisibleGraph<Int>

      if (dataPack.isFull) {
        for (revision in revisions) {
          pathsMap[getIndex(revision)] = (revision as VcsFileRevisionEx).path
        }
        visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, null, pathsMap.keys)
      }
      else {
        val commits = ContainerUtil.newArrayListWithCapacity<GraphCommit<Int>>(revisions.size)

        for (revision in revisions) {
          val index = getIndex(revision)
          pathsMap[index] = (revision as VcsFileRevisionEx).path
          commits.add(GraphCommitImpl.createCommit(index, emptyList(), revision.getRevisionDate().time))
        }

        val refs = getFilteredRefs(dataPack)
        val providers = ContainerUtil.newHashMap(Pair.create<VirtualFile, VcsLogProvider>(root, logProviders[root]))

        dataPack = DataPack.build(commits, refs, providers, storage, false)
        visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, null,
                                                         null/*no need to filter here, since we do not have any extra commits in this pack*/)
      }

      return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, pathsMap)
    }

    private fun getFilteredRefs(dataPack: DataPack): Map<VirtualFile, CompressedRefs> {
      val refs = ContainerUtil.newHashMap<VirtualFile, CompressedRefs>()
      var compressedRefs: CompressedRefs? = dataPack.refsModel.allRefsByRoot[root]
      if (compressedRefs == null) {
        compressedRefs = CompressedRefs(ContainerUtil.newHashSet(), storage)
      }
      refs[root] = compressedRefs
      return refs
    }

    private fun getIndex(revision: VcsFileRevision): Int {
      return storage.getCommitIndex(HashImpl.build(revision.revisionNumber.asString()), root)
    }

    private fun filterWithIndex(dataPack: DataPack,
                                sortType: PermanentGraph.SortType,
                                filters: VcsLogFilterCollection): VisiblePack {
      val matchingHeads = vcsLogFilterer.getMatchingHeads(dataPack.refsModel, setOf(root), filters)
      val data = indexDataGetter.buildFileNamesData(filePath)
      var visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, matchingHeads, data.commits)

      var pathsMap: Map<Int, FilePath>? = null
      if (visibleGraph.visibleCommitCount > 0) {
        if (visibleGraph is VisibleGraphImpl<*>) {
          val row = getCurrentRow(dataPack, visibleGraph, data)
          if (row >= 0) {
            val refiner = FileHistoryRefiner(visibleGraph as VisibleGraphImpl<Int>, data)
            if (refiner.refine(row, filePath)) {
              // creating a vg is the most expensive task, so trying to avoid that when unnecessary
              visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, matchingHeads, refiner.pathsForCommits.keys)
              pathsMap = refiner.pathsForCommits
            }
          }
        }
      }

      if (pathsMap == null) {
        pathsMap = data.buildPathsMap()
      }

      if (!filePath.isDirectory) reindexFirstCommitsIfNeeded(visibleGraph)
      return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, pathsMap)
    }

    private fun reindexFirstCommitsIfNeeded(graph: VisibleGraph<Int>) {
      // we may not have renames big commits, may need to reindex them
      if (graph is VisibleGraphImpl<*>) {
        val liteLinearGraph = LinearGraphUtils.asLiteLinearGraph((graph as VisibleGraphImpl<*>).linearGraph)
        for (row in 0 until liteLinearGraph.nodesCount()) {
          // checking if commit is a root commit (which means file was added or renamed there)
          if (liteLinearGraph.getNodes(row, LiteLinearGraph.NodeFilter.DOWN).isEmpty()) {
            index.reindexWithRenames(graph.getRowInfo(row).commit, root)
          }
        }
      }
    }

    private fun getCurrentRow(pack: DataPack,
                              visibleGraph: VisibleGraph<Int>,
                              fileIndexData: IndexDataGetter.FileNamesData): Int {
      val permanentGraph = pack.permanentGraph
      if (permanentGraph is PermanentGraphImpl<*>) {
        val hash = hash ?: getHead(pack)
        if (hash != null) {
          return findAncestorRowAffectingFile(permanentGraph as PermanentGraphImpl<Int>, hash, visibleGraph, fileIndexData)
        }
      }
      return 0
    }

    private fun getHead(pack: DataPack): Hash? {
      val refs = pack.refsModel.allRefsByRoot[root]
      val headOptional = refs!!.streamBranches().filter { br -> br.name == "HEAD" }.findFirst()
      if (headOptional.isPresent) {
        val head = headOptional.get()
        assert(head.root == root)
        return head.commitHash
      }
      return null
    }

    private fun findAncestorRowAffectingFile(permanentGraph: PermanentGraphImpl<Int>,
                                             hash: Hash,
                                             visibleGraph: VisibleGraph<Int>,
                                             fileNamesData: IndexDataGetter.FileNamesData): Int {
      val result = Ref<Int>()

      val commitsInfo = permanentGraph.permanentCommitsInfo
      val reachableNodes = ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentGraph.linearGraph))
      reachableNodes.walk(setOf(commitsInfo.getNodeId(storage.getCommitIndex(hash, root))), true) { currentNode ->
        val id = commitsInfo.getCommitId(currentNode)
        if (fileNamesData.affects(id, filePath)) {
          result.set(currentNode)
          false // stop walk, we have found it
        }
        else {
          true // continue walk
        }
      }

      if (!result.isNull) {
        val rowIndex = visibleGraph.getVisibleRowIndex(commitsInfo.getCommitId(result.get()))
        return assertNotNull(rowIndex)
      }

      return -1
    }
  }

  companion object {
    private val LOG = Logger.getInstance(FileHistoryFilterer::class.java)

    private fun getFilePath(filters: VcsLogFilterCollection): FilePath? {
      val detailsFilters = filters.detailsFilters
      if (detailsFilters.size != 1) {
        return null
      }

      val filter = notNull(getFirstItem(detailsFilters)) as? VcsLogStructureFilter ?: return null

      val files = filter.files
      return if (files.size != 1) {
        null
      }
      else notNull(getFirstItem(files))

    }

    private fun getHash(filters: VcsLogFilterCollection): Hash? {
      val revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER) ?: return null

      val heads = revisionFilter.heads
      return if (heads.size != 1) {
        null
      }
      else notNull(getFirstItem(heads)).hash
    }

    @JvmStatic
    fun createFilters(path: FilePath,
                      revision: Hash?,
                      root: VirtualFile,
                      showAllBranches: Boolean): VcsLogFilterCollection {
      val fileFilter = VcsLogStructureFilterImpl(setOf(path))

      if (revision != null) {
        val revisionFilter = VcsLogRevisionFilterImpl.fromCommit(CommitId(revision, root))
        return VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder(fileFilter, revisionFilter).build()
      }

      val branchFilter = if (showAllBranches) null else VcsLogBranchFilterImpl.fromBranch("HEAD")
      return VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder(fileFilter, branchFilter).build()
    }
  }
}
