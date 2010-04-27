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
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
    return infos.toArray(new UsageInfo[infos.size()]);
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myChangeInfo.updateMethod((PsiMethod)elements[0]);
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    changeMethod();
    for (UsageInfo usage : usages) {

      for (ChangeSignatureUsageProcessor processor : GrChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
        if (processor.processUsage(myChangeInfo, usage, false, usages)) break;
      }
    }
  }

  private void changeMethod() {
    final PsiMethod method = myChangeInfo.getMethod();
    if (myChangeInfo.isChangeName()) {
      method.setName(myChangeInfo.getNewName());
    }

    if (myChangeInfo.isChangeVisibility()) {
      method.getModifierList().setModifierProperty(myChangeInfo.getVisibilityModifier(), true);
    }
  }

  @Override
  protected String getCommandName() {
    return "";
    //return RefactoringBundle.message("changing.signature.of.0", UsageViewUtil.getDescriptiveName(myChangeInfo.getMethod()));
  }


  private MultiMap<PsiElement, String> findConflicts(Ref<UsageInfo[]> refUsages) {
    return new MultiMap<PsiElement, String>();//todo
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

    return showConflicts(conflictDescriptions);
  }
}
