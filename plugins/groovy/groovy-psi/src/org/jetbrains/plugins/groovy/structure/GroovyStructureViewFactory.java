/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.structure;

/**
 * User: Dmitry.Krasilschikov
 * Date: 19.06.2008
 */

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.ide.structureView.impl.java.JavaInheritedMembersNodeProvider;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import java.util.Arrays;
import java.util.Collection;

public class GroovyStructureViewFactory implements PsiStructureViewFactory {
  @Override
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {

      @Override
      @NotNull
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new JavaFileTreeModel((GroovyFileBase)psiFile, editor) {
          @NotNull
          @Override
          public Collection<NodeProvider> getNodeProviders() {
            return Arrays.<NodeProvider>asList(new JavaInheritedMembersNodeProvider());
          }
        };
      }
    };
  }
}
