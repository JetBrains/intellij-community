/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureViewDescriptor;
import com.intellij.refactoring.changeSignature.PossiblyIncorrectUsage;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureProcessor extends BaseRefactoringProcessor {
  public static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureProcessor");
  private final GrChangeInfoImpl myChangeInfo;

  //private

  public GrChangeSignatureProcessor(Project project, GrChangeInfoImpl changeInfo) {
    super(project);
    myChangeInfo = changeInfo;
  }

  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ChangeSignatureViewDescriptor(myChangeInfo.getMethod());
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    List<UsageInfo> infos = new ArrayList<UsageInfo>();

    final ChangeSignatureUsageProcessor[] processors = ChangeSignatureUsageProcessor.EP_NAME.getExtensions();
    for (ChangeSignatureUsageProcessor processor : processors) {
      infos.addAll(Arrays.asList(processor.findUsages(myChangeInfo)));
    }

    infos = filterUsages(infos);
    return infos.toArray(new UsageInfo[infos.size()]);
  }

  private static List<UsageInfo> filterUsages(List<UsageInfo> infos) {
    Map<PsiElement, MoveRenameUsageInfo> moveRenameInfos = new HashMap<PsiElement, MoveRenameUsageInfo>();
    Set<PsiElement> usedElements = new com.intellij.util.containers.hash.HashSet<PsiElement>();

    List<UsageInfo> result = new ArrayList<UsageInfo>(infos.size() / 2);
    for (UsageInfo info : infos) {
      PsiElement element = info.getElement();
      if (info instanceof MoveRenameUsageInfo) {
        if (usedElements.contains(element)) continue;
        moveRenameInfos.put(element, (MoveRenameUsageInfo)info);
      }
      else {
        moveRenameInfos.remove(element);
        usedElements.add(element);
        if (!(info instanceof PossiblyIncorrectUsage) || ((PossiblyIncorrectUsage)info).isCorrect()) {
          result.add(info);
        }
      }
    }
    result.addAll(moveRenameInfos.values());
    return result;
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myChangeInfo.updateMethod((PsiMethod)elements[0]);
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
        if (processor.processUsage(myChangeInfo, usage, true, usages)) break;
      }
    }
    changeMethod();
    for (UsageInfo usage : usages) {
      for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
        if (processor.processUsage(myChangeInfo, usage, false, usages)) break;
      }
    }
  }

  private void changeMethod() {
    for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      if (processor.processPrimaryMethod(myChangeInfo)) break;
    }
  }

  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("changing.signature.of.0", UsageViewUtil.getDescriptiveName(myChangeInfo.getMethod()));
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<PsiElement, String>();
    for (ChangeSignatureUsageProcessor usageProcessor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      final MultiMap<PsiElement, String> conflicts = usageProcessor.findConflicts(myChangeInfo, refUsages);
      for (PsiElement key : conflicts.keySet()) {
        Collection<String> collection = conflictDescriptions.get(key);
        if (collection.size() == 0) collection = new HashSet<String>();
        collection.addAll(conflicts.get(key));
        conflictDescriptions.put(key, collection);
      }
    }

    final UsageInfo[] usagesIn = refUsages.get();
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usagesIn));
    RenameUtil.removeConflictUsages(usagesSet);
    if (!conflictDescriptions.isEmpty()) {
      ConflictsDialog dialog = new ConflictsDialog(myProject, conflictDescriptions, new Runnable() {
        public void run() {
          execute(usagesIn);
        }
      });
      dialog.show();
      if (!dialog.isOK()) {
        if (dialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }
    refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));
    prepareSuccessful();
    return true;
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo[] usages) {
    for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      if (processor.shouldPreviewUsages(myChangeInfo, usages)) return true;
    }
    return super.isPreviewUsages(usages);
  }
}
