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
                   @Nullable final ViewSettings viewSettings) {
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
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    final List<AbstractTreeNode> children = new ArrayList<>();
    final Module module = getModule();

    final GrTypeDefinition grTypeDefinition = extractPsiFromValue();
    assert grTypeDefinition != null;

    buildChildren(module, grTypeDefinition, children);

    return children.isEmpty() ? null : children;
  }

  protected void buildChildren(final Module module, final GrTypeDefinition grClass, final List<AbstractTreeNode> children) {

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