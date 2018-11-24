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

import com.intellij.psi.PsiFile;

import java.util.Collections;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class TestModeOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private final StringBuilder builder = new StringBuilder();
  private String lastString;
  private int lastLength;

  TestModeOptimizingSearchHelper() {
    super();
  }

  @Override
  public boolean doOptimizing() {
    return true;
  }

  @Override
  public void clear() {
    lastString = builder.toString();
    builder.setLength(0);
    lastLength = 0;
  }

  @Override
  protected void doAddSearchWordInCode(final String refname) {
    append(refname, "in code:");
  }

  @Override
  protected void doAddSearchWordInText(String refname) {
    append(refname, "in text:");
  }

  private void append(final String refname, final String str) {
    if (builder.length() == lastLength) builder.append("[");
    else builder.append("|");
    builder.append(str).append(refname);
  }

  @Override
  protected void doAddSearchWordInComments(final String refname) {
    append(refname, "in comments:");
  }

  @Override
  protected void doAddSearchWordInLiterals(final String refname) {
    append(refname, "in literals:");
  }

  @Override
  public void endTransaction() {
    super.endTransaction();
    builder.append("]");
    lastLength = builder.length();
  }

  @Override
  public boolean isScannedSomething() {
    return false;
  }

  @Override
  public Set<PsiFile> getFilesSetToScan() {
    return Collections.emptySet();
  }

  public String getSearchPlan() {
    return lastString;
  }
}
