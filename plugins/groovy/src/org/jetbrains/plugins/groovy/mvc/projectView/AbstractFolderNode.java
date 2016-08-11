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
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class AbstractFolderNode extends AbstractMvcPsiNodeDescriptor {
  private final String myPresentableText;

  protected AbstractFolderNode(@NotNull final Module module,
                               @NotNull final PsiDirectory directory,
                               @NotNull String presentableText,
                               final ViewSettings viewSettings, int weight) {
    super(module, viewSettings, directory, weight);
    myPresentableText = presentableText;
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final PsiElement psiElement) {
    final VirtualFile virtualFile = getVirtualFile();
    assert virtualFile != null;

    return "Folder: " + virtualFile.getPresentableName();
  }

  @NotNull
  protected PsiDirectory getPsiDirectory() {
    return (PsiDirectory)extractPsiFromValue();
  }

  @Override
  @Nullable
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    final PsiDirectory directory = getPsiDirectory();
    if (!directory.isValid()) {
      return Collections.emptyList();
    }

    final List<AbstractTreeNode> children = new ArrayList<>();

    // scan folder's children
    for (PsiDirectory subDir : directory.getSubdirectories()) {
      children.add(createFolderNode(subDir));
    }

    for (PsiFile file : directory.getFiles()) {
      processNotDirectoryFile(children, file);
    }

    return children;
  }

  private AbstractFolderNode createFolderNode(PsiDirectory directory) {
    PsiDirectory realDirectory = directory;

    StringBuilder textBuilder = null;

    if (getSettings().isHideEmptyMiddlePackages()) {
      do {
        if (realDirectory.getFiles().length > 0) break;

        PsiDirectory[] subdirectories = realDirectory.getSubdirectories();
        if (subdirectories.length != 1) break;

        if (textBuilder == null) {
          textBuilder = new StringBuilder();
          textBuilder.append(realDirectory.getName());
        }

        realDirectory = subdirectories[0];

        textBuilder.append('.').append(realDirectory.getName());
      } while (true);
    }

    String presentableText = textBuilder == null ? directory.getName() : textBuilder.toString();

    return new AbstractFolderNode(getModule(), realDirectory, presentableText, getSettings(), FOLDER) {
      @Override
      protected void processNotDirectoryFile(List<AbstractTreeNode> nodes, PsiFile file) {
        AbstractFolderNode.this.processNotDirectoryFile(nodes, file);
      }

      @Override
      protected AbstractTreeNode createClassNode(GrTypeDefinition typeDefinition) {
        return AbstractFolderNode.this.createClassNode(typeDefinition);
      }
    };
  }

  @Override
  protected void updateImpl(final PresentationData data) {
    final PsiDirectory psiDirectory = getPsiDirectory();

    data.setPresentableText(myPresentableText);

    for (final IconProvider provider : Extensions.getExtensions(IconProvider.EXTENSION_POINT_NAME)) {
      final Icon icon = provider.getIcon(psiDirectory, 0);
      if (icon != null) {
        data.setIcon(icon);
        return;
      }
    }
  }

  @Override
  protected boolean containsImpl(@NotNull final VirtualFile file) {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null || !psiElement.isValid()) {
      return false;
    }

    final VirtualFile valueFile = ((PsiDirectory)psiElement).getVirtualFile();
    if (!VfsUtil.isAncestor(valueFile, file, false)) {
      return false;
    }

    final Project project = psiElement.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(valueFile);
    if (module == null) {
      return fileIndex.getModuleForFile(file) == null;
    }

    return ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
  }

  protected void processNotDirectoryFile(final List<AbstractTreeNode> nodes, final PsiFile file) {
    if (file instanceof GroovyFile) {
      final GrTypeDefinition[] definitions = ((GroovyFile)file).getTypeDefinitions();
      if (definitions.length > 0) {
        for (final GrTypeDefinition typeDefinition : definitions) {
          nodes.add(createClassNode(typeDefinition));
        }
        return;
      }
    }
    nodes.add(new FileNode(getModule(), file, getSettings()));
  }

  protected AbstractTreeNode createClassNode(final GrTypeDefinition typeDefinition) {
    assert getValue() != null;

    return new ClassNode(getModule(), typeDefinition, getSettings());
  }

}
