// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModel.ElementInfoProvider
import com.intellij.ide.structureView.StructureViewModel.ExpandInfoProvider
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.customRegions.CustomRegionTreeElement
import com.intellij.ide.structureView.logical.PhysicalAndLogicalStructureViewBuilder
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.structureView.newStructureView.StructureViewSelectVisitorState
import com.intellij.ide.structureView.newStructureView.StructureViewUtil
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper
import com.intellij.ide.structureView.newStructureView.getElementInfoProvider
import com.intellij.ide.util.FileStructureFilter
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.FileStructurePopup
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntRef
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.structureView.impl.DelegatingNodeProvider
import com.intellij.platform.structureView.impl.dto.DeferredNodesDto
import com.intellij.platform.structureView.impl.dto.NodeProviderNodesDto
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import com.intellij.platform.structureView.impl.dto.StructureViewTreeElementDto
import com.intellij.platform.structureView.impl.dto.TreeNodesDto
import com.intellij.platform.structureView.impl.util.StructurePopupUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.PlaceHolder
import com.intellij.ui.speedSearch.ElementFilter
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.util.ui.tree.TreeUtil
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.asDeferred
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

@ApiStatus.Internal
object BackendStructureTreeServiceTestApi {
  @TestOnly
  fun getStructureViewCountForTests(): Int {
    return ClientSessionsManager.getAppSessions(ClientKind.REMOTE).sumOf { appSession ->
      appSession.projectSessions.sumOf { session ->
        session.getServiceIfCreated(BackendStructureTreeService::class.java)
          ?.getStructureViewCountForTests() ?: 0
      }
    }
  }
}

internal class BackendStructureTreeService(private val session: ClientProjectSession, internal val cs: CoroutineScope) {
  private val structureViews = ConcurrentHashMap<Int, StructureViewEntry>()

  fun getStructureViewEntry(id: StructureViewDtoId): StructureViewEntry? {
    return structureViews[id.id]
  }

  @TestOnly
  internal fun getStructureViewCountForTests(): Int {
    return structureViews.size
  }

  internal suspend fun createStructureViewModel(
    id: StructureViewDtoId,
    fileEditor: FileEditor,
    navigationCallback: ((AbstractTreeNode<*>) -> Unit)?,
  ): StructureViewModelDto? {
    val project = session.project
    // the model with this id has been created but hasn't been received by the frontend
    structureViews[id.id]?.disposable?.let {
      withContext(Dispatchers.EDT + NonCancellable) {
        Disposer.dispose(it)
      }
    }

    val startTime = System.nanoTime()

    logger.trace { "Creating structure model for id: $id" }

    val disposable = Disposer.newDisposable(session, "Disposable for structure model with id: $id")

    var dto: StructureViewModelDto? = null

    try {
      dto = run {
        val createTreeModelStartTime = System.nanoTime()
        data class InitialTreeModelContext(
          val treeModel: StructureViewModel,
          val backendActionOwner: BackendTreeActionOwner,
          val wrapper: SmartTreeStructure,
          val filteringStructure: FilteringTreeStructure,
        )

        val treeModelContext: InitialTreeModelContext? = withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            val structureViewBuilder = fileEditor.structureViewBuilder ?: return@writeIntentReadAction null
            val treeModel = when (structureViewBuilder) {
              is PhysicalAndLogicalStructureViewBuilder -> {
                val view = structureViewBuilder.createPhysicalStructureView(fileEditor, project)
                Disposer.register(disposable, view)
                StructurePopupUtil.createStructureViewModel(project, fileEditor, view)
              }
              is TreeBasedStructureViewBuilder -> {
                structureViewBuilder.createStructureViewModel(EditorUtil.getEditorEx(fileEditor))
              }
              else -> {
                val view = structureViewBuilder.createStructureView(fileEditor, project)
                Disposer.register(disposable, view)
                StructurePopupUtil.createStructureViewModel(project, fileEditor, view)
              }
            }

            //todo flag for tw
            (treeModel as? PlaceHolder)?.setPlace(TreeStructureUtil.PLACE)

            val backendActionOwner = BackendTreeActionOwner(allNodeProvidersActive = false)
            val treeModelWrapper = TreeModelWrapper(treeModel, backendActionOwner)

            Disposer.register(disposable, treeModelWrapper)

            // to get the same tree deduplication as in com.intellij.ui.treeStructure.filtered.FilteringTreeStructure.addToCache
            lateinit var filteringStructure: FilteringTreeStructure
            val wrapper = object : SmartTreeStructure(project, treeModelWrapper) {
              override fun rebuildTree() {
                if (!structureViews.containsKey(id.id)) return
                ProgressManager.getInstance().computePrioritized(ThrowableComputable<Unit?, Throwable?> {
                  super.rebuildTree()
                  filteringStructure.rebuild()
                })
              }

              override fun createTree(): TreeElementWrapper {
                return StructureViewComponent.createWrapper(myProject, myModel.getRoot(), myModel)
              }
            }

            filteringStructure = FilteringTreeStructure(ElementFilter<Any> { true }, wrapper, false)
            InitialTreeModelContext(treeModel, backendActionOwner, wrapper, filteringStructure)
          }
        }
        logger.trace { "createStructureViewModel[$id]: tree model creation completed in ${(System.nanoTime() - createTreeModelStartTime).asTraceDuration()}" }

        if (treeModelContext == null) return@run null

        val myStructureTreeModel = StructureTreeModel<FilteringTreeStructure>(treeModelContext.filteringStructure, disposable)

        val requestFlow = MutableSharedFlow<StructureViewEvent>(
          extraBufferCapacity = 1,
          onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val nodesFlow = MutableStateFlow<TreeNodesDto?>(null)
        val entry = StructureViewEntry(treeModelContext.wrapper,
                                       myStructureTreeModel,
                                       treeModelContext.treeModel,
                                       requestFlow,
                                       treeModelContext.backendActionOwner,
                                       fileEditor,
                                       disposable,
                                       navigationCallback)

        Disposer.register(disposable, Disposable {
          structureViews.remove(id.id)
        })

        structureViews[id.id] = entry

        val job = cs.launch(CoroutineName("StructureView event processor for id: $id"), start = CoroutineStart.UNDISPATCHED) {
          entry.requestFlow
            .onCompletion {
              nodesFlow.emit(null)
            }
            .collectLatest { event ->
              when (event) {
                is StructureViewEvent.ComputeNodes -> {
                  val computeStartTime = System.nanoTime()
                  val nodes = entry.structureTreeModel.invoker.compute {
                    ProgressManager.getInstance().computePrioritized(ThrowableComputable<ComputeNodesResult?, Throwable?> { return@ThrowableComputable computeNodes(id.id) })
                  }.asDeferred().await()

                  logger.trace {
                    val computeTime = System.nanoTime() - computeStartTime
                    val nodesCount = nodes?.nodes?.size ?: 0
                    val providerNodesCount = nodes?.nodeProviders?.sumOf { it.nodes.size } ?: 0
                    "createStructureViewModel[$id]: compute nodes request completed in ${computeTime.asTraceDuration()}; " +
                    "nodes=$nodesCount, providerNodes=$providerNodesCount, providers=${nodes?.nodeProviders?.size ?: 0}, selection=${nodes?.editorSelectionId}"
                  }

                  val nodesDto = nodes?.let {
                    TreeNodesDto(it.editorSelectionId, it.nodes, it.nodeProviders, it.deferredProviderNodes)
                  }
                  nodesFlow.emit(nodesDto)
                }
              }
            }
        }

        Disposer.register(disposable, Disposable { job.cancel() })

        val (root, actions) = try {
          myStructureTreeModel.invoker.compute {
            //to initialize FilteringTreeStructure
            treeModelContext.wrapper.rebuildTree()
            val rootModel = createRootModel(treeModelContext.wrapper.rootElement as TreeElementWrapper,
                                            treeModelContext.treeModel as? ExpandInfoProvider,
                                            getElementInfoProvider(treeModelContext.treeModel))
            val actions = createActionModels(treeModelContext.treeModel)

            rootModel to actions
          }.asDeferred().await()
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)

          logger.error("Error creating structure model for file: $fileEditor", e)
          return@run null
        }

        if (root == null) {
          logger.error("Root model for structure model with id: $id (file $fileEditor) is null")
          return@run null
        }

        check(entry.requestFlow.tryEmit(StructureViewEvent.ComputeNodes))

        logger.trace { "createStructureViewModel[$id]: model DTO created in ${(System.nanoTime() - startTime).asTraceDuration()}" }

        return@run StructureViewModelDto(
          root,
          nodesFlow.toRpc(),
          (treeModelContext.treeModel as? ExpandInfoProvider)?.isSmartExpand ?: false,
          (treeModelContext.treeModel as? ExpandInfoProvider)?.minimumAutoExpandDepth ?: 2,
          false, /*todo for tw*/
          actions
        )
      }
    }
    finally {
      if (dto == null) {
        withContext(Dispatchers.EDT + NonCancellable) { Disposer.dispose(disposable) }
      }
    }

    return dto
  }

  suspend fun disposeStructureViewModel(id: StructureViewDtoId) {
    withContext(Dispatchers.EDT + NonCancellable) {
      val entry = structureViews[id.id] ?: return@withContext
      Disposer.dispose(entry.disposable)
      logger.trace { "Structure model with id: $id was disposed" }
    }
  }

  private data class ComputeNodesResult(
    val editorSelectionId: Int?,
    val nodes: List<StructureViewTreeElementDto>,
    val nodeProviders: List<NodeProviderNodesDto>,
    val deferredProviderNodes: Deferred<DeferredNodesDto>,
  )

  private fun computeNodes(entryId: Int): ComputeNodesResult? {
    val entry = structureViews[entryId] ?: return null
    check(entry.structureTreeModel.invoker.isValidThread)

    val computeNodesStartTime = System.nanoTime()

    logger.trace { "computeNodes: Starting computation for structure view entry with id: $entryId" }

    val mainNodes = mutableListOf<StructureViewTreeElementDto>()
    //todo for not a popup these don't have to implement FileStructureNodeProvider
    val nodeProvidersMap = getNodeProviders(entry.treeModel)?.filter { it !is DelegatingNodeProvider<*> }?.associate { it to mutableListOf<StructureViewTreeElementDto>() } ?: emptyMap()
    val expandInfoProvider = entry.treeModel as? ExpandInfoProvider
    val elementInfoProvider = getElementInfoProvider(entry.treeModel)

    //todo for not a popup these don't have to implement FileStructureFilter
    val filters = entry.treeModel.filters.filterIsInstance<FileStructureFilter>()

    logger.trace {
      "computeNodes: nodeProviders=${nodeProvidersMap.size}, filters=${filters.size}, " +
      "expandInfoProvider=${expandInfoProvider != null}, elementInfoProvider=${elementInfoProvider != null}"
    }

    val (currentEditorElement, editorOffset) = entry.treeModel.currentEditorElement to ((entry.fileEditor as? TextEditor)?.getEditor()
                                                                                          ?.getCaretModel()?.offset ?: -1)
    val state = StructureViewSelectVisitorState()

    logger.trace { "computeNodes: Starting tree traversal" }
    val visitorStartTime = System.nanoTime()

    val root = entry.structureTreeModel.root ?: return null
    visit(root, entry.structureTreeModel, TreePath(root)) {
      StructureViewComponent.visitPathForElementSelection(it, currentEditorElement, editorOffset, state)

      processTreeElement(expandInfoProvider, elementInfoProvider, it, mainNodes, nodeProvidersMap, filters, entry)
      false
    }


    val selectedKey = processStateToGetSelectedKey(state, entry, currentEditorElement)

    logger.trace {
      val selection = entry.nodeToId[selectedKey]
      "computeNodes: tree traversal completed in ${(System.nanoTime() - visitorStartTime).asTraceDuration()};" +
      "selectionId=$selection, selectedKeyPresent=${selectedKey != null}"
    }

    val nodeProviders = nodeProvidersMap.entries.mapNotNull { (provider, nodes) ->
      val nodesLoaded = entry.backendActionOwner.isActionActive(provider)

      if (!nodesLoaded) return@mapNotNull null
      NodeProviderNodesDto(
        provider.name,
        nodes,
      )
    }

    val selection = entry.nodeToId[selectedKey]

    val deferredNodeProviders = CompletableFuture<DeferredNodesDto>()

    if (nodeProvidersMap.keys.any { !entry.backendActionOwner.isActionActive(it) }) {
      entry.structureTreeModel.invoker.invokeLater {
        val entry = structureViews[entryId] ?: return@invokeLater
        // Check if any providers don't have their nodes loaded yet

        logger.trace { "Some providers don't have nodes loaded yet, rebuilding tree with all providers active" }

        // Enable all node providers
        entry.backendActionOwner.allNodeProvidersActive = true

        // Rebuild tree with all providers active
        entry.wrapper.rebuildTree()

        val deferredInvalidateStartTime = System.nanoTime()
        entry.structureTreeModel.invalidateAsync().handle { _, throwable ->
          logger.trace {
            "computeNodes: deferred provider tree invalidation completed in " +
            (System.nanoTime() - deferredInvalidateStartTime).asTraceDuration()
          }
          if (throwable != null) {
            deferredNodeProviders.completeExceptionally(throwable)
          }
          // Compute nodes for ALL providers (not just inactive ones)
          // because previously active providers may have new nodes now
          if (entry.structureTreeModel.isDisposed) {
            logger.trace { "computeNodes: Skipping tree traversal for deferred nodes because tree is disposed" }
            return@handle
          }
          entry.structureTreeModel.invoker.invoke {
            if (entry.structureTreeModel.isDisposed) return@invoke
            logger.trace { "computeNodes: Tree traversal for deferred nodes started" }
            val allProviderNodes = computeAllProviderNodes(entry)
            deferredNodeProviders.complete(allProviderNodes)
          }.onError {
            deferredNodeProviders.completeExceptionally(it)
          }
        }
      }.onError {
        deferredNodeProviders.completeExceptionally(it)
      }
    }
    else {
      deferredNodeProviders.complete(null)
    }

    logger.trace { "computeNodes: total compute time ${(System.nanoTime() - computeNodesStartTime).asTraceDuration()}" }

    //todo for tw - proper selection logic, not just editor's element
    return ComputeNodesResult(
      selection,
      mainNodes,
      nodeProviders,
      deferredNodeProviders.asDeferred()
    )
  }

  private fun computeAllProviderNodes(entry: StructureViewEntry): DeferredNodesDto? {
    check(entry.structureTreeModel.invoker.isValidThread)

    val computeStartTime = System.nanoTime()
    //all node providers are enabled anyway
    val providerNodesMap = getNodeProviders(entry.treeModel)?.filter { it !is DelegatingNodeProvider<*> }?.associateWith { mutableListOf<StructureViewTreeElementDto>() } ?: return null
    val expandInfoProvider = entry.treeModel as? ExpandInfoProvider
    val elementInfoProvider = getElementInfoProvider(entry.treeModel)
    val filters = entry.treeModel.filters.filterIsInstance<FileStructureFilter>()
    // Dummy list for non-provider elements (we only care about provider elements here)
    val mainNodes = mutableListOf<StructureViewTreeElementDto>()

    val root = entry.structureTreeModel.root ?: return null

    visit(root, entry.structureTreeModel, TreePath(root)) {
      processTreeElement(expandInfoProvider, elementInfoProvider, it, mainNodes, providerNodesMap, filters, entry)
      false
    }

    val nodeProviderDtos = providerNodesMap.map { (provider, nodes) ->
      NodeProviderNodesDto(provider.name, nodes)
    }
    logger.trace {
      "computeAllProviderNodes: completed in ${(System.nanoTime() - computeStartTime).asTraceDuration()}; " +
      "providers=${nodeProviderDtos.size}"
    }

    return DeferredNodesDto(
      nodeProviderDtos,
      mainNodes
    )
  }

  private fun createRootModel(
    wrapper: TreeElementWrapper,
    expandInfoProvider: ExpandInfoProvider?,
    elementInfoProvider: ElementInfoProvider?,
  ): StructureViewTreeElementDto? {
    val id = 0

    val element = wrapper.getValue() as? StructureViewTreeElement ?: return null


    return element.toDto(id,
                         -1,
                         0,
                         shouldAutoExpand(element, expandInfoProvider),
                         elementInfoProvider?.isAlwaysShowsPlus(element),
                         elementInfoProvider?.isAlwaysLeaf(element),
                         StructureViewUtil.getSpeedSearchText(wrapper),
                         emptyList())
  }

  private fun processTreeElement(
    expandInfoProvider: ExpandInfoProvider?,
    elementInfoProvider: ElementInfoProvider?,
    path: TreePath,
    nodes: MutableList<StructureViewTreeElementDto>,
    nodeProvidersMap: Map<FileStructureNodeProvider<*>, MutableList<StructureViewTreeElementDto>>?,
    filters: List<FileStructureFilter>,
    structureViewEntry: StructureViewEntry,
  ) {
    check(structureViewEntry.structureTreeModel.invoker.isValidThread)

    val wrapper = unwrapTreeElementWrapper(path.lastPathComponent) ?: return
    val element = wrapper.getValue() as? StructureViewTreeElement ?: return

    val nodeKey = element.nodeKey(wrapper)
    val id = structureViewEntry.nodeToId.getOrPut(nodeKey) {
      structureViewEntry.idRef.get().also { structureViewEntry.idRef.inc() }
    }

    val parentWrapper = unwrapTreeElementWrapper(path.parentPath?.lastPathComponent)
    val parentId = (parentWrapper?.getValue() as? StructureViewTreeElement)
      ?.let { structureViewEntry.nodeToId[it.nodeKey(parentWrapper)] } ?: 0

    val model = element.toDto(
      id,
      parentId,
      wrapper.index,
      shouldAutoExpand(element, expandInfoProvider),
      elementInfoProvider?.isAlwaysShowsPlus(element),
      elementInfoProvider?.isAlwaysLeaf(element),
      StructureViewUtil.getSpeedSearchText(wrapper),
      filters.map { it.isVisible(element) }
    )

    if (wrapper.provider != null) {
      nodeProvidersMap?.get(wrapper.provider)?.add(model) ?: nodes.add(model)
    }
    else {
      nodes.add(model)
    }
  }

  internal sealed class StructureViewEvent {
    data object ComputeNodes : StructureViewEvent()
  }

  internal data class StructureViewEntry(
    val wrapper: SmartTreeStructure,
    val structureTreeModel: StructureTreeModel<FilteringTreeStructure>,
    val treeModel: StructureViewModel,
    val requestFlow: MutableSharedFlow<StructureViewEvent>,
    val backendActionOwner: BackendTreeActionOwner, // should only be accessed at StructureTreeModel.invoker
    val fileEditor: FileEditor,
    val disposable: Disposable,
    val navigationCallback: ((AbstractTreeNode<*>) -> Unit)?,
    val idRef: IntRef = IntRef(1), // should only be accessed at StructureTreeModel.invoker
    val nodeToId: MutableMap<StructureViewNodeKey, Int> = hashMapOf(), // should only be accessed at StructureTreeModel.invoker
  )

  companion object {
    fun getInstance(project: Project): BackendStructureTreeService = project.service()

    private val logger = logger<BackendStructureTreeService>()

    internal fun visit(element: TreeNode, model: StructureTreeModel<*>, path: TreePath, action: (TreePath) -> Boolean): Boolean {
      if (model.isDisposed) return true

      for (child in model.getChildren(element)) {
        val childPath = path.pathByAddingChild(child)
        if (action(childPath)) return true
        if (visit(child, model, childPath, action)) return true
      }
      return false
    }

    internal fun unwrapTreeElementWrapper(node: Any?): TreeElementWrapper? {
      val userObject = TreeUtil.getUserObject(node)
      return when (userObject) {
        is TreeElementWrapper -> userObject
        is FilteringTreeStructure.FilteringNode -> userObject.delegate as? TreeElementWrapper
        else -> null
      }
    }

    private fun unwrapStructureKey(node: Any?): StructureViewNodeKey? {
      val wrapper = unwrapTreeElementWrapper(node) ?: return null
      val element = wrapper.value as? StructureViewTreeElement ?: return null
      return element.nodeKey(wrapper)
    }

    private fun shouldAutoExpand(element: StructureViewTreeElement, expandInfoProvider: ExpandInfoProvider?): Boolean {
      // mirrors com.intellij.ide.structureView.newStructureView.StructureViewComponent.MyExpandListener.isAutoExpandNode
      return element is CustomRegionTreeElement || expandInfoProvider?.isAutoExpand(element) == true
    }

    internal fun processStateToGetSelectedKey(state: StructureViewSelectVisitorState, entry: StructureViewEntry, currentEditorElement: Any?): StructureViewNodeKey? {
      val adjusted = state.bestMatch ?: return null
      val selectedNode = if (!state.isExactMatch && currentEditorElement is PsiElement) {
        FileStructurePopup.findClosestPsiElement(currentEditorElement, adjusted, entry.structureTreeModel) ?: adjusted.lastPathComponent
      }
      else {
        adjusted.lastPathComponent
      }
      return unwrapStructureKey(selectedNode)
    }
  }
}

internal data class StructureViewNodeKey(
  val value: Any?,
  val elementClass: Class<*>,
  val providerName: String?,
)

internal fun StructureViewTreeElement.nodeKey(wrapper: TreeElementWrapper?): StructureViewNodeKey {
  // Some structure elements intentionally wrap the same PSI value with different presentation/topology, for example HTML5 outline nodes.
  return StructureViewNodeKey(value, javaClass, wrapper?.provider?.name)
}

private fun Long.asTraceDuration(): String {
  return "${TimeUnit.NANOSECONDS.toMillis(this)} ms"
}
