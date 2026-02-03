// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.structure

import com.intellij.ide.structureView.impl.java.JavaFileTreeModel
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase

internal class GroovyFileTreeModel(file: GroovyFileBase, editor: Editor?) : JavaFileTreeModel(file, editor) {
  override fun getNodeProviders(): List<JavaInheritedMembersNodeProvider> = listOf(JavaInheritedMembersNodeProvider())
}