/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class MoveGroovyScriptProcessor extends MoveClassesOrPackagesProcessor {
  public MoveGroovyScriptProcessor(Project project,
                                   PsiElement[] elements,
                                   MoveDestination moveDestination,
                                   boolean searchInComments,
                                   boolean searchInNonJavaFiles,
                                   MoveCallback moveCallback) {
    super(project, elements, moveDestination, searchInComments, searchInNonJavaFiles, moveCallback);
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    List<UsageInfo> allUsages = new ArrayList<UsageInfo>();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final List<PsiElement> elements = getElements();
    for (PsiElement element : elements) {
      final GroovyFile groovyFile = (GroovyFile)element;

      final PsiClass scriptClass = groovyFile.getClasses()[0];
      String newName = getTargetPackage().getQualifiedName() + scriptClass.getName();

      final UsageInfo[] usages = MoveClassesOrPackagesUtil.findUsages(scriptClass, isSearchInComments(), isSearchInNonJavaFiles(), newName);
      allUsages.addAll(new ArrayList<UsageInfo>(Arrays.asList(usages)));
    }
    myMoveDestination
      .analyzeModuleConflicts(elements, conflicts, allUsages.toArray(new UsageInfo[allUsages.size()]));
    if (!conflicts.isEmpty()) {
      for (PsiElement element : conflicts.keySet()) {
        allUsages.add(new ConflictsUsageInfo(element, conflicts.get(element)));
      }
    }

    return UsageViewUtil.removeDuplicatedUsages(allUsages.toArray(new UsageInfo[allUsages.size()]));
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    return showConflicts(new MultiMap<PsiElement, String>(), refUsages.get());
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    try {
      Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<PsiElement, PsiElement>();
      final List<PsiElement> elements = getElements();
      for (PsiElement element : elements) {
        GroovyFile file = (GroovyFile)element;
        final RefactoringElementListener elementListener = getTransaction().getElementListener(file);

        PsiClass oldScriptClass = file.getClasses()[0];

        GroovyChangeContextUtil.encodeContextInfo(file);
        PsiManager.getInstance(myProject).moveFile(file, myMoveDestination.getTargetDirectory(file));
        file.setPackageName(getTargetPackage().getQualifiedName());

        PsiClass newScriptClass = file.getClasses()[0];
        oldToNewElementsMapping.put(oldScriptClass, newScriptClass);

        elementListener.elementMoved(file);
      }

      for (PsiElement element : elements) {
        GroovyChangeContextUtil.decodeContextInfo(element, null, null);
      }

      myNonCodeUsages = retargetUsages(usages, oldToNewElementsMapping);
    }
    catch (IncorrectOperationException e) {
      myNonCodeUsages = new NonCodeUsageInfo[0];
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
  }
}
