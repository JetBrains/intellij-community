// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    CacheManager.SERVICE.getInstance(myProject).processFilesWithWord(myFileProcessor, refname,
                                                                     (short)(UsageSearchContext.IN_CODE | UsageSearchContext.IN_PLAIN_TEXT),
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
    if (!map.isEmpty()) map.clear();
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
