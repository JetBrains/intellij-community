// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.util.text.NaturalComparator

internal val BranchTreeNodeComparator = compareBy<BranchNodeDescriptor> {
  getOrderWeight(it)
} then compareBy(NaturalComparator.INSTANCE) {
  it.displayName
}

private fun getOrderWeight(descriptor: BranchNodeDescriptor): Int = when {
  descriptor is BranchNodeDescriptor.Ref && descriptor.refInfo.isCurrent -> 0
  descriptor is BranchNodeDescriptor.Ref && descriptor.refInfo.isFavorite -> 1
  descriptor is BranchNodeDescriptor.Group && descriptor.hasFavorites -> 2
  descriptor is BranchNodeDescriptor.Group -> 3
  else -> 4
}
