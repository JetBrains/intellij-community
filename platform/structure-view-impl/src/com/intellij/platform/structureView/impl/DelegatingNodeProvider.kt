// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl

import com.intellij.ide.util.treeView.smartTree.NodeProvider
import com.intellij.ide.util.treeView.smartTree.TreeElement

/**
 * a marker interface for a node provider to delegate to backend through rpc.
 * for node providers that might have additional logic apart from just providing nodes
 */
interface DelegatingNodeProvider<T : TreeElement> : NodeProvider<T>