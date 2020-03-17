// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Collection;

/**
 * @author Dmitry Krasilschikov
 */
public class MethodNode extends AbstractMvcPsiNodeDescriptor {
  public MethodNode(@NotNull final Module module,
                    @NotNull final GrMethod method,
                    final ViewSettings viewSettings) {
    super(module, viewSettings, method, METHOD);
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final PsiElement psiElement) {
    return "GrMethod: " + ((GrMethod)psiElement).getName();
  }

  @Override
  protected Collection<AbstractTreeNode<?>> getChildrenImpl() {
    return null;
  }

}