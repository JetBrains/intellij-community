@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.ide.util.treeView.TreeState
import com.intellij.ui.tree.TreeNodePresentationBuilderImpl
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import org.jetbrains.annotations.ApiStatus


internal class ProjectViewNodeModelBuilderImpl<T : Any>(private val id: Long, private val userObject: T) : ProjectViewNodeModelBuilder {
  private val presentationBuilder = TreeNodePresentationBuilderImpl()
  private var pathElementType: String? = null
  private var pathElementId: String? = null
  private var canNavigate = false
  private var canNavigateToSource = false
  private var includedInExpandAll = false
  private var isDirectory = false

  override fun setModel(model: BackendProjectViewNodeModel<*>) {
    model as ProjectViewNodeModelImpl<*>
    presentationBuilder.setPresentation(model.presentation)
    pathElementType = model.pathElementType
    pathElementId = model.pathElementId
    canNavigate = model.canNavigate()
    canNavigateToSource = model.canNavigateToSource()
    includedInExpandAll = model.isIncludedInExpandAll()
    isDirectory = model.isDirectory()
  }

  override fun buildPresentation(build: (TreeNodePresentationBuilder) -> Unit) {
    build(presentationBuilder)
  }

  override fun setPathElementType(pathElementType: String) {
    this.pathElementType = pathElementType
  }

  override fun setPathElementId(pathElementId: String) {
    this.pathElementId = pathElementId
  }

  override fun setCanNavigate(canNavigate: Boolean) {
    this.canNavigate = canNavigate
  }

  override fun setCanNavigateToSource(canNavigateToSource: Boolean) {
    this.canNavigateToSource = canNavigateToSource
  }

  override fun setIncludedInExpandAll(includedInExpandAll: Boolean) {
    this.includedInExpandAll = includedInExpandAll
  }

  override fun setIsDirectory(isDirectory: Boolean) {
    this.isDirectory = isDirectory
  }

  fun build(): ProjectViewNodeModelImpl<T> {
    val pathElementType = this.pathElementType
                          ?: (userObject as? PathElementIdProvider)?.pathElementType
                          ?: TreeState.defaultPathElementType(userObject)
    val pathElementId = this.pathElementId
                        ?: (userObject as? PathElementIdProvider)?.pathElementId
                        ?: TreeState.defaultPathElementId(userObject)
    return ProjectViewNodeModelImpl(
      maybeUserObject = userObject,
      id = id,
      presentation = presentationBuilder.build(),
      pathElementType = pathElementType,
      pathElementId = pathElementId,
      canNavigate = canNavigate,
      canNavigateToSource = canNavigateToSource,
      isIncludedInExpandAll = includedInExpandAll,
      isDirectory = isDirectory,
    )
  }
}

@ApiStatus.Internal
data class ProjectViewNodeModelImpl<T : Any>(
  private val maybeUserObject: T?,
  override val id: Long,
  override val presentation: TreeNodePresentationImpl,
  private val pathElementType: String,
  private val pathElementId: String,
  val flags: Int = 0,
) : BackendProjectViewNodeModel<T>, PathElementIdProvider {
  constructor(
    maybeUserObject: T?,
    id: Long,
    presentation: TreeNodePresentationImpl,
    pathElementType: String,
    pathElementId: String,
    canNavigate: Boolean,
    canNavigateToSource: Boolean,
    isIncludedInExpandAll: Boolean,
    isDirectory: Boolean,
  ) : this(
    maybeUserObject,
    id,
    presentation,
    pathElementType,
    pathElementId,
    flags(canNavigate, canNavigateToSource, isIncludedInExpandAll, isDirectory),
  )

  override val userObject: T
    get() = checkNotNull(maybeUserObject) { "The user object is only available on the backend" }

  override fun getPathElementType(): String {
    return pathElementType
  }

  override fun getPathElementId(): String {
    return pathElementId
  }

  override fun canNavigate(): Boolean = (flags and FLAG_CAN_NAVIGATE) != 0

  override fun canNavigateToSource(): Boolean = (flags and FLAG_CAN_NAVIGATE_TO_SOURCE) != 0
  
  override fun isIncludedInExpandAll(): Boolean = (flags and FLAG_INCLUDED_IN_EXPAND_ALL) != 0

  override fun isDirectory(): Boolean = (flags and FLAG_IS_DIRECTORY) != 0
}

private const val FLAG_CAN_NAVIGATE = (1 shl 0)
private const val FLAG_CAN_NAVIGATE_TO_SOURCE = (1 shl 1)
private const val FLAG_INCLUDED_IN_EXPAND_ALL = (1 shl 2)
private const val FLAG_IS_DIRECTORY = (1 shl 3)

private fun flags(canNavigate: Boolean, canNavigateToSource: Boolean, isIncludedInExpandAll: Boolean, isDirectory: Boolean): Int =
  (if (canNavigate) FLAG_CAN_NAVIGATE else 0) or
  (if (canNavigateToSource) FLAG_CAN_NAVIGATE_TO_SOURCE else 0) or 
  (if (isIncludedInExpandAll) FLAG_INCLUDED_IN_EXPAND_ALL else 0) or
  (if (isDirectory) FLAG_IS_DIRECTORY else 0)

@ApiStatus.Experimental
const val SUPER_ROOT_ID: Long = 0L

@ApiStatus.Internal
object SuperRoot

@ApiStatus.Internal
val SuperRootPresentation: TreeNodePresentationImpl = TreeNodePresentationBuilderImpl().also {
  it.setMainText("fake root - for convenience, not to display")
}.build()

@ApiStatus.Internal
val SuperRootModel: ProjectViewNodeModel = ProjectViewNodeModelImpl(null, SUPER_ROOT_ID, SuperRootPresentation, "", "")
