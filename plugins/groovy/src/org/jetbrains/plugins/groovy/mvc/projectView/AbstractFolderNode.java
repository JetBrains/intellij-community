// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.swing.*;
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
  protected Collection<AbstractTreeNode<?>> getChildrenImpl() {
    final PsiDirectory directory = getPsiDirectory();
    if (!directory.isValid()) {
      return Collections.emptyList();
    }

    // scan folder's children
    final List<AbstractTreeNode<?>> children =
      ContainerUtil.map(directory.getSubdirectories(), this::createFolderNode);

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
      protected void processNotDirectoryFile(List<AbstractTreeNode<?>> nodes, PsiFile file) {
        AbstractFolderNode.this.processNotDirectoryFile(nodes, file);
      }

      @Override
      protected AbstractTreeNode createClassNode(GrTypeDefinition typeDefinition) {
        return AbstractFolderNode.this.createClassNode(typeDefinition);
      }
    };
  }

  @Override
  protected void updateImpl(@NotNull final PresentationData data) {
    final PsiDirectory psiDirectory = getPsiDirectory();

    data.setPresentableText(myPresentableText);

    for (final IconProvider provider : IconProvider.EXTENSION_POINT_NAME.getExtensionList()) {
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

  protected void processNotDirectoryFile(final List<AbstractTreeNode<?>> nodes, final PsiFile file) {
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
