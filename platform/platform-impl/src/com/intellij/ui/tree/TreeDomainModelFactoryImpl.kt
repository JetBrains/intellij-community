// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ui.treeStructure.TreeDomainModel
import com.intellij.ui.treeStructure.TreeDomainModelFactory

internal class TreeDomainModelFactoryImpl : TreeDomainModelFactory {
  override fun createTreeDomainModel(structure: AbstractTreeStructure, useReadAction: Boolean, concurrency: Int): TreeDomainModel =
    TreeStructureDomainModelAdapter(structure, useReadAction, concurrency)
}
