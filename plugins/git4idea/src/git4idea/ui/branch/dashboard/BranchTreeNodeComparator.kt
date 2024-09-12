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

private fun getOrderWeight(descriptor: BranchNodeDescriptor): Int = when {
  descriptor is BranchNodeDescriptor.Branch && descriptor.branchInfo.isCurrent -> 0
  descriptor is BranchNodeDescriptor.Branch && descriptor.branchInfo.isFavorite -> 1
  descriptor.type == NodeType.GROUP_NODE -> 2
  else -> 3
}
