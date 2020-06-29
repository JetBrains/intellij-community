// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author Bas Leijdekkers
 */
public abstract class FilterAction extends DumbAwareAction implements Filter {

  private static final AtomicInteger myFilterCount = new AtomicInteger();

  protected final SimpleColoredComponent myLabel = new SimpleColoredComponent();
  protected final FilterTable myTable;
  private final int myPosition;

  private boolean myApplicable = true;

  protected FilterAction(@NotNull Supplier<String> text, FilterTable table) {
    super(text);
    myTable = table;
    myPosition = myFilterCount.incrementAndGet();
  }

  @Override
  public final int position() {
    return myPosition;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    initFilter();
    myApplicable = false;
    myTable.addFilter(this);
  }

  @Override
  public final SimpleColoredComponent getRenderer() {
    myLabel.clear();
    setLabel(myLabel);
    return myLabel;
  }

  protected abstract void setLabel(SimpleColoredComponent component);

  public abstract boolean hasFilter();

  protected void initFilter() {}

  public abstract void clearFilter();

  public void reset() {
    myApplicable = true;
  }

  protected abstract boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target);

  public boolean isAvailable() {
    return myApplicable && !hasFilter();
  }

  public boolean checkApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    return myApplicable = isApplicable(nodes, completePattern, target);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!hasFilter() && myApplicable);
  }
}
