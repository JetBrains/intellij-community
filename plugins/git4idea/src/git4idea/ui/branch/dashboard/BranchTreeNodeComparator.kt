// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

internal object BranchTreeNodeComparator : Comparator<BranchNodeDescriptor> {
  override fun compare(d1: BranchNodeDescriptor, d2: BranchNodeDescriptor): Int {
    val weight1 = getOrderWeight(d1)
    val weight2 = getOrderWeight(d2)
    val weightDelta = weight1.compareTo(weight2)
    if (weightDelta != 0) return weightDelta

    val displayText1 = d1.getDisplayText()
    val displayText2 = d2.getDisplayText()
    if (displayText1 != null && displayText2 != null) {
      return displayText1.compareTo(displayText2)
    }
    if (displayText1 != null) return -1
    if (displayText2 != null) return 1

    return d1.type.compareTo(d2.type)
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
}
