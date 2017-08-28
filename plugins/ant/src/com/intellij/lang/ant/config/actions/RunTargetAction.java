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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.ant.resources.AntActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 28.05.12 16:07
 */
public class RunTargetAction extends AnAction {
  public RunTargetAction() {
    super();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Pair<AntBuildFileBase, AntDomTarget> antTarget = findAntTarget(e);
    if (antTarget == null) return;

    ExecutionHandler.runBuild(
      antTarget.first, new String[] {antTarget.second.getName().getValue() },
      null,
      e.getDataContext(),
      Collections.emptyList(),
      AntBuildListener.NULL);
  }


  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    final Presentation presentation = e.getPresentation();

    Pair<AntBuildFileBase, AntDomTarget> antTarget = findAntTarget(e);
    if (antTarget == null) {
      presentation.setEnabled(false);
      presentation.setText(AntActionsBundle.message("action.RunTargetAction.text.template", ""));
    } else {
      presentation.setEnabled(true);
      presentation.setText(AntActionsBundle.message("action.RunTargetAction.text.template", "'" + antTarget.second.getName().getValue() + "'"));
    }
  }

  @Nullable
  private static Pair<AntBuildFileBase, AntDomTarget> findAntTarget(@NotNull AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final Project project = e.getProject();

    if (project == null || editor == null) {
      return null;
    }
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) {
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final XmlFile xmlFile = (XmlFile)psiFile;

    final AntBuildFileBase antFile = AntConfigurationBase.getInstance(project).getAntBuildFile(xmlFile);
    if (antFile == null) {
      return null;
    }

    final PsiElement element = xmlFile.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (xmlTag == null) {
      return null;
    }

    DomElement dom = AntSupport.getAntDomElement(xmlTag);
    while (dom != null && !(dom instanceof AntDomTarget)) {
      dom = dom.getParent();
    }

    final AntDomTarget domTarget = (AntDomTarget)dom;
    if (domTarget == null) {
      return null;
    }
    return Pair.create(antFile, domTarget);
  }
}
