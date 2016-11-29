/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class MoveGroovyFileHandler extends MoveFileHandler {
  private static final Logger LOG = Logger.getInstance(MoveGroovyFileHandler.class);

  @Override
  public boolean canProcessElement(PsiFile element) {
    return element instanceof GroovyFile;
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    final GroovyFile groovyFile = (GroovyFile)file;
    GroovyChangeContextUtil.encodeContextInfo(groovyFile);
    for (PsiClass psiClass : groovyFile.getClasses()) {
      oldToNewMap.put(psiClass, MoveClassesOrPackagesUtil.doMoveClass(psiClass, moveDestination));
    }
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile psiFile, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    final List<UsageInfo> result = new ArrayList<>();
    final PsiPackage newParentPackage = JavaDirectoryService.getInstance().getPackage(newParent);
    final String qualifiedName = newParentPackage == null ? "" : newParentPackage.getQualifiedName();
    for (PsiClass aClass : ((GroovyFile)psiFile).getClasses()) {
      Collections.addAll(result, MoveClassesOrPackagesUtil
        .findUsages(aClass, searchInComments, searchInNonJavaFiles, StringUtil.getQualifiedName(qualifiedName, aClass.getName())));
    }
    return result.isEmpty() ? null : result;
  }

  @Override
  public void retargetUsages(List<UsageInfo> usageInfos, Map<PsiElement, PsiElement> oldToNewMap) {
    for (UsageInfo usage : usageInfos) {
      if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsage = (MoveRenameUsageInfo)usage;
        final PsiElement oldElement = moveRenameUsage.getReferencedElement();
        final PsiElement newElement = oldToNewMap.get(oldElement);
        final PsiReference reference = moveRenameUsage.getReference();
        if (reference != null) {
          try {
            LOG.assertTrue(newElement != null, oldElement != null ? oldElement : reference);
            reference.bindToElement(newElement);
          }
          catch (IncorrectOperationException ex) {
            LOG.error(ex);
          }
        }
      }
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
    GroovyChangeContextUtil.decodeContextInfo(file, null, null);
    final PsiDirectory containingDirectory = file.getContainingDirectory();
    if (containingDirectory == null) return;

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
    if (aPackage == null) return;

    final String qualifiedName = aPackage.getQualifiedName();

    if (file instanceof GroovyFile) {
      ((GroovyFile)file).setPackageName(qualifiedName);
    }
  }
}
