// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author peter
 */
public class FileNode extends AbstractMvcPsiNodeDescriptor {
  public FileNode(@NotNull final Module module,
                  @NotNull final PsiFile file,
                  final ViewSettings viewSettings) {
    super(module, viewSettings, file, FILE);
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final PsiElement psiElement) {
    return "File: " + ((PsiFile)psiElement).getName();
  }

  @Override
  protected PsiFile extractPsiFromValue() {
    return (PsiFile)super.extractPsiFromValue();
  }

  @Override
  protected Collection<AbstractTreeNode<?>> getChildrenImpl() {
    return null;
  }

  @Override
  public Comparable getTypeSortKey() {
    String extension = PsiFileNode.extension(extractPsiFromValue());
    return extension == null ? null : new PsiFileNode.ExtensionSortKey(extension);
  }

  @Override
  protected void updateImpl(@NotNull final PresentationData data) {
    final PsiFile value = extractPsiFromValue();
    assert value != null;
    data.setPresentableText(value.getName());
    data.setIcon(value.getIcon(Iconable.ICON_FLAG_READ_STATUS));
  }
}
