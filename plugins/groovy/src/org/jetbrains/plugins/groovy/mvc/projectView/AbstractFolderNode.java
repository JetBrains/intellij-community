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
import com.intellij.openapi.util.Iconable;
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
  @Nullable private final String myLocationMark;

  private final PsiDirectory myHiddenParent;

  protected AbstractFolderNode(@NotNull final Module module,
                               @NotNull final PsiDirectory directory,
                               @NotNull  PsiDirectory hiddenParent,
                               @Nullable final String locationMark,
                               final ViewSettings viewSettings, int weight) {
    super(module, viewSettings, new NodeId(directory, locationMark), weight);
    myLocationMark = locationMark;
    myHiddenParent = hiddenParent;
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final NodeId nodeId, @NotNull final PsiElement psiElement) {
    final VirtualFile virtualFile = getVirtualFile();
    assert virtualFile != null;

    return "Folder: " + virtualFile.getPresentableName();
  }

  @NotNull
  protected PsiDirectory getPsiDirectory() {
    return (PsiDirectory)extractPsiFromValue();
  }

  @Nullable
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    final PsiDirectory directory = getPsiDirectory();
    if (!directory.isValid()) {
      return Collections.emptyList();
    }

    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();

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

    if (getSettings().isHideEmptyMiddlePackages()) {
      do {
        if (realDirectory.getFiles().length > 0) break;

        PsiDirectory[] subdirectories = realDirectory.getSubdirectories();
        if (subdirectories.length != 1) break;

        realDirectory = subdirectories[0];
      } while (true);
    }

    return new AbstractFolderNode(getModule(), realDirectory, directory, myLocationMark, getSettings(), FOLDER) {
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

    String text;

    if (psiDirectory == myHiddenParent) {
      text = psiDirectory.getName();
    }
    else {
      StringBuilder sb = new StringBuilder();
      for (PsiDirectory dir = myHiddenParent; dir != psiDirectory; dir = dir.getSubdirectories()[0]) {
        sb.append(dir.getName()).append('.');
      }
      sb.append(psiDirectory.getName());
      text = sb.toString();
    }

    data.setPresentableText(text);

    for (final IconProvider provider : Extensions.getExtensions(IconProvider.EXTENSION_POINT_NAME)) {
      final Icon openIcon = provider.getIcon(psiDirectory, Iconable.ICON_FLAG_OPEN);
      if (openIcon != null) {
        final Icon closedIcon = provider.getIcon(psiDirectory, Iconable.ICON_FLAG_CLOSED);
        if (closedIcon != null) {
          data.setOpenIcon(openIcon);
          data.setClosedIcon(closedIcon);
          return;
        }
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
    nodes.add(new FileNode(getModule(), file, myLocationMark, getSettings()));
  }

  protected AbstractTreeNode createClassNode(final GrTypeDefinition typeDefinition) {
    final NodeId nodeId = getValue();
    assert nodeId != null;

    return new ClassNode(getModule(), typeDefinition, nodeId.getLocationRootMark(), getSettings());
  }

}
