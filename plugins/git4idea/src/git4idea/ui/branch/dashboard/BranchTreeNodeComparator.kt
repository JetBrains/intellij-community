// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.util.text.NaturalComparator

internal val BranchTreeNodeComparator = compareBy<BranchNodeDescriptor> {
  getOrderWeight(it)
} then compareBy(NaturalComparator.INSTANCE) {
  it.displayName
} then compareBy {
  it.type
}

private fun getOrderWeight(descriptor: BranchNodeDescriptor): Int {
  val branchInfo = descriptor.branchInfo

  val isCurrent = branchInfo != null && branchInfo.isCurrent
  if (isCurrent) return 0

  val isFavorite = branchInfo != null && branchInfo.isFavorite
  if (isFavorite) return 1

  val isGroupNode = descriptor.type == NodeType.GROUP_NODE
  if (isGroupNode) return 2

  return 3
}
