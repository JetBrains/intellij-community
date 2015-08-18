/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @auhor Bas Leijdekkers
 */
public class ReplaceArmWithTryFinallyIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new AutomaticResourceManagementPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiTryStatement tryStatement = (PsiTryStatement)token.getParent();
    if (tryStatement == null) {
      return;
    }
    final boolean replaceAll = tryStatement.getCatchBlocks().length == 0 && tryStatement.getFinallyBlock() == null;
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return;
    }
    final List<String> resources = ContainerUtil.newArrayList();
    final StringBuilder newTryStatement = new StringBuilder("{");
    for (PsiResourceListElement resource : resourceList) {
      if (resource instanceof PsiResourceVariable) {
        newTryStatement.append(resource.getText()).append(";\n");
        resources.add(((PsiResourceVariable)resource).getName());
      }
      else {
        resources.add(resource.getText());
      }
      newTryStatement.append("try {");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    final PsiElement[] children = tryBlock.getChildren();
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement child = children[i];
      newTryStatement.append(child.getText());
    }
    for (int i = resources.size() - 1; i >= 0; i--) {
      newTryStatement.append("} finally {\n").append(resources.get(i)).append(".close();\n}");
    }
    newTryStatement.append('}');
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiCodeBlock newCodeBlock = factory.createCodeBlockFromText(newTryStatement.toString(), element);
    if (replaceAll) {
      for (PsiStatement newStatement : newCodeBlock.getStatements()) {
        tryStatement.getParent().addBefore(newStatement, tryStatement);
      }
      tryStatement.delete();
    }
    else {
      resourceList.delete();
      tryBlock.replace(newCodeBlock);
    }
  }
}
