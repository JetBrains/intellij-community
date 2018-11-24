/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import gnu.trove.THashSet;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
class FindInFilesOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private final MyFileProcessor myFileProcessor;
  private THashSet<PsiFile> filesToScan;
  private THashSet<PsiFile> filesToScan2;

  private final Project myProject;
  private final SearchScope myScope;
  private final boolean myCaseSensitive;

  FindInFilesOptimizingSearchHelper(SearchScope scope, boolean caseSensitive, Project project) {
    myScope = scope;
    myCaseSensitive = caseSensitive;
    myProject = project;

    if (scope instanceof GlobalSearchScope && filesToScan == null) {
      filesToScan = new THashSet<>();
      filesToScan2 = new THashSet<>();
    }
    myFileProcessor = new MyFileProcessor();
  }

  @Override
  public boolean doOptimizing() {
    return myScope instanceof GlobalSearchScope;
  }

  @Override
  public void clear() {
    super.clear();

    if (filesToScan != null) {
      filesToScan.clear();
      filesToScan2.clear();
    }
  }

  @Override
  protected void doAddSearchWordInCode(final String refname) {
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_CODE,
                                                                     (GlobalSearchScope)myScope, myCaseSensitive);
  }

  @Override
  protected void doAddSearchWordInText(final String refname) {
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_PLAIN_TEXT,
                                                                     (GlobalSearchScope)myScope, myCaseSensitive);
  }

  @Override
  protected void doAddSearchWordInComments(final String refname) {
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_COMMENTS,
                                                                     (GlobalSearchScope)myScope, myCaseSensitive);
  }

  @Override
  protected void doAddSearchWordInLiterals(final String refname) {
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname, UsageSearchContext.IN_STRINGS,
                                                                     (GlobalSearchScope)myScope, myCaseSensitive);
  }

  @Override
  public void endTransaction() {
    super.endTransaction();
    final THashSet<PsiFile> map = filesToScan;
    if (map.size() > 0) map.clear();
    filesToScan = filesToScan2;
    filesToScan2 = map;
  }

  @Override
  public Set<PsiFile> getFilesSetToScan() {
    return filesToScan;
  }

  private class MyFileProcessor implements Processor<PsiFile> {
    @Override
    public boolean process(PsiFile file) {
      if (scanRequest == 0 || filesToScan.contains(file)) {
        filesToScan2.add(file);
      }
      return true;
    }
  }
}
