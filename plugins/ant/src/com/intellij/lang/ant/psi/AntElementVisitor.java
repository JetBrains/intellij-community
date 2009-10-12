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
package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.PsiAntElement;
import com.intellij.psi.PsiElement;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 13, 2007
 */
public class AntElementVisitor {
  public void visitAntElement(final PsiAntElement antElement) {
    final PsiElement[] psiElements = antElement.getChildren();
    for (PsiElement child : psiElements) {
      if (child instanceof AntElement) {
        ((AntElement)child).acceptAntElementVisitor(this);
      }
    }
  }

  public void visitAntStructuredElement(final AntStructuredElement element) {
    visitAntElement(element);
  }

  public void visitAntTarget(final AntTarget target) {
    visitAntElement(target);
  }

  public void visitAntTask(final AntTask task) {
    visitAntElement(task);
  }

  public void visitAntCall(final AntCall antCall) {
    visitAntElement(antCall);
  }

  public void visitAntImport(final AntImport antImport) {
    visitAntElement(antImport);
  }

  public void visitAntProperty(final AntProperty antProperty) {
    visitAntElement(antProperty);
  }

  public void visitAntTypedef(final AntTypeDef typeDef) {
    visitAntElement(typeDef);
  }

  public void visitAntMacroDef(final AntMacroDef antMacroDef) {
    visitAntElement(antMacroDef);
  }

  public void visitAntScriptDef(final AntScriptDef antScriptDef) {
    visitAntElement(antScriptDef);
  }

  public void visitAntPresetDef(final AntPresetDef presetDef) {
    visitAntElement(presetDef);
  }

  public void visitAntProject(final AntProject antProject) {
    visitAntElement(antProject);
  }

  public void visitAntFile(final AntFile antFile) {
    visitAntElement(antFile);
  }
}
