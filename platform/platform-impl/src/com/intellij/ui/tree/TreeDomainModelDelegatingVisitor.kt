// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.openapi.application.EDT
import com.intellij.ui.treeStructure.TreeDomainModel
import com.intellij.ui.treeStructure.TreeNodeViewModel
import com.intellij.ui.treeStructure.TreeViewModelVisitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.tree.TreePath
