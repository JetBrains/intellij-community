// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.psi.search.UsageSearchContext.*;

/**
 * @author Maxim.Mossienko
*/
class FindInFilesOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private Set<VirtualFile> filesToScan;
  private Set<VirtualFile> filesToScan2;

  private final Project myProject;
  private final SearchScope myScope;
  private final boolean myCaseSensitive;

  private boolean myTransactionStarted;

  FindInFilesOptimizingSearchHelper(SearchScope scope, boolean caseSensitive, @NotNull Project project) {
    myScope = scope;
    myCaseSensitive = caseSensitive;
    myProject = project;

    if (scope instanceof GlobalSearchScope) {
      filesToScan = new HashSet<>();
      filesToScan2 = new HashSet<>();
    }
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
  protected void doAddSearchWordInCode(@NotNull String word) {
    myTransactionStarted = true;
    VirtualFile[] files = CacheManager.getInstance(myProject)
      .getVirtualFilesWithWord(word, (short)(IN_CODE | IN_PLAIN_TEXT), (GlobalSearchScope)myScope, myCaseSensitive);
    process(files);
  }

  @Override
  protected void doAddSearchWordInText(@NotNull String word) {
    myTransactionStarted = true;
    VirtualFile[] files =
      CacheManager.getInstance(myProject).getVirtualFilesWithWord(word, IN_PLAIN_TEXT, (GlobalSearchScope)myScope, myCaseSensitive);
    process(files);
  }

  @Override
  protected void doAddSearchWordInComments(@NotNull String word) {
    myTransactionStarted = true;
    VirtualFile[] files =
      CacheManager.getInstance(myProject).getVirtualFilesWithWord(word, IN_COMMENTS, (GlobalSearchScope)myScope, myCaseSensitive);
    process(files);
  }

  @Override
  protected void doAddSearchWordInLiterals(@NotNull String word) {
    myTransactionStarted = true;
    VirtualFile[] files =
      CacheManager.getInstance(myProject).getVirtualFilesWithWord(word, IN_STRINGS, (GlobalSearchScope)myScope, myCaseSensitive);
    process(files);
  }

  @Override
  public void endTransaction() {
    if (!myTransactionStarted) return;
    myTransactionStarted = false;
    super.endTransaction();
    Set<VirtualFile> map = filesToScan;
    if (!map.isEmpty()) map.clear();
    filesToScan = filesToScan2;
    filesToScan2 = map;
  }

  @NotNull
  @Override
  public Set<VirtualFile> getFilesSetToScan() {
    assert !myTransactionStarted;
    if (filesToScan == null) {
      return Collections.emptySet();
    }
    return filesToScan;
  }

  private void process(VirtualFile @NotNull [] files) {
    if (scanRequest == 0) {
      Collections.addAll(filesToScan2, files);
    }
    else {
      for (VirtualFile file : files) {
        if (filesToScan.contains(file)) {
          filesToScan2.add(file);
        }
      }
    }
  }
}
