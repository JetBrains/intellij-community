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

import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 15.04.2009
 */
public class TestClassNode extends ClassNode {
  private final Icon myMethodIcon;

  public TestClassNode(@NotNull final Module module,
                       @NotNull final GrTypeDefinition controllerClass,
                       @Nullable final ViewSettings viewSettings, final Icon methodIcon) {
    super(module, controllerClass, viewSettings);
    myMethodIcon = methodIcon;
  }

  @Nullable
  @Override
  protected MethodNode createNodeForMethod(final Module module, final GrMethod method) {
    if (method == null) return null;

    if (!DumbService.isDumb(module.getProject())) {
      final boolean isTestMethod = JUnitUtil.isTestMethod(new PsiLocation<PsiMethod>(module.getProject(), method));

      if (isTestMethod) {
        return new TestMethodNode(module, method, getSettings(), myMethodIcon);
      }
    }

    return new MethodNode(module, method, getSettings());
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final PsiElement psiElement) {
    return "Test class: " + ((GrTypeDefinition)psiElement).getName();
  }                                                                                                                                                

}
