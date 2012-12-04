/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.debugger.fragments;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnAmbiguousClosureContainer;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

/**
 * @author ven
 */
public class GroovyCodeFragment extends GroovyFileImpl implements JavaCodeFragment, IntentionFilterOwner, GrUnAmbiguousClosureContainer {
  private PsiType myThisType;
  private PsiType mySuperType;
  private ExceptionHandler myExceptionChecker;
  private IntentionFilterOwner.IntentionActionsFilter myFilter;
  private GlobalSearchScope myResolveScope;

  public GroovyCodeFragment(Project project, CharSequence text) {
    super(new SingleRootFileViewProvider(PsiManager.getInstance(project),
        new LightVirtualFile(
            "Dummy.groovy",
            GroovyFileType.GROOVY_FILE_TYPE,
            text), true));
    ((SingleRootFileViewProvider) getViewProvider()).forceCachedPsi(this);
  }

  public void setThisType(PsiType thisType) {
    myThisType = thisType;
  }

  public PsiType getSuperType() {
    return mySuperType;
  }

  public void setSuperType(PsiType superType) {
    mySuperType = superType;
  }

  public String importsToString() {
    return ""; //todo
  }

  public void addImportsFromString(String imports) {
  }

  public void setVisibilityChecker(JavaCodeFragment.VisibilityChecker checker) {
  }

  public VisibilityChecker getVisibilityChecker() {
    return VisibilityChecker.EVERYTHING_VISIBLE;
  }

  public void setExceptionHandler(ExceptionHandler checker) {
    myExceptionChecker = checker;
  }

  public ExceptionHandler getExceptionHandler() {
    return myExceptionChecker;
  }

  public void setIntentionActionsFilter(IntentionActionsFilter filter) {
    myFilter = filter;
  }

  public IntentionActionsFilter getIntentionActionsFilter() {
    return myFilter;
  }

  public void forceResolveScope(GlobalSearchScope scope) {
    myResolveScope = scope;
  }

  public GlobalSearchScope getForcedResolveScope() {
    return myResolveScope;
  }

  public boolean importClass(PsiClass aClass) {
    return false;
  }

  public PsiType getThisType() {
    return myThisType;
  }
}
