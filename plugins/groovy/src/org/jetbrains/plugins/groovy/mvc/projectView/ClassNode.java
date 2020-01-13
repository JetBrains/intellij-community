// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class ClassNode extends AbstractMvcPsiNodeDescriptor {
  public ClassNode(@NotNull final Module module,
                   @NotNull final GrTypeDefinition rClass,
                   final ViewSettings viewSettings) {
    super(module, viewSettings, rClass, CLASS);
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final PsiElement psiElement) {
    return "GrTypeDefinition: " + ((GrTypeDefinition)psiElement).getName();
  }

  @Override
  protected GrTypeDefinition extractPsiFromValue() {
    return (GrTypeDefinition)super.extractPsiFromValue();
  }

  @Override
  @Nullable
  protected Collection<AbstractTreeNode<?>> getChildrenImpl() {
    final List<AbstractTreeNode<?>> children = new ArrayList<>();
    final Module module = getModule();

    final GrTypeDefinition grTypeDefinition = extractPsiFromValue();
    assert grTypeDefinition != null;

    buildChildren(module, grTypeDefinition, children);

    return children.isEmpty() ? null : children;
  }

  protected void buildChildren(final Module module, final GrTypeDefinition grClass, final List<AbstractTreeNode<?>> children) {

    final GrMethod[] methods = grClass.getCodeMethods();
    for (final GrMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) continue;

      final MethodNode node = createNodeForMethod(module, method);
      if (node != null) children.add(node);
    }
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Nullable
  protected MethodNode createNodeForMethod(final Module module, final GrMethod method) {
    return null;
  }

}