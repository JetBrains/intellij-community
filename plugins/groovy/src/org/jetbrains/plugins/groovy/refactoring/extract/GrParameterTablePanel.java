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

package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;

/**
 * @author ilyas
 */
public abstract class GrParameterTablePanel extends ParameterTablePanel {

  public GrParameterTablePanel() {
    super(GroovyNamesUtil::isIdentifier);
  }

  public void init(ExtractInfoHelper helper) {
    super.init(helper.getParameterInfos(), helper.getProject(), helper.getContext());
  }

  @Override
  protected TypeSelector createSelector(Project project, VariableData data, PsiElement[] scopeElements) {
    final PsiType type = data.type;
    return new TypeSelector(type != null ? type : PsiType.getJavaLangObject(PsiManager.getInstance(project), scopeElements[0].getResolveScope()), project);
  }
}
