/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase

class GroovyStructureViewFactory : PsiStructureViewFactory {
  override fun getStructureViewBuilder(file: PsiFile): StructureViewBuilder? {
    val groovyFile = file as? GroovyFileBase ?: return null

    return object : TreeBasedStructureViewBuilder() {
      override fun createStructureViewModel(editor: Editor?) = GroovyFileTreeModel(groovyFile, editor)
    }
  }
}