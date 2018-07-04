// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bas Leijdekkers
 */
public abstract class FilterAction extends AnAction implements Filter {

  private static final AtomicInteger myFilterCount = new AtomicInteger();

  protected final SimpleColoredComponent myLabel = new SimpleColoredComponent();
  protected final FilterTable myTable;
  private final int myPosition;

  protected FilterAction(@Nullable String text, FilterTable table) {
    super(text);
    myTable = table;
    myPosition = myFilterCount.incrementAndGet();
  }

  @Override
  public final int position() {
    return myPosition;
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    myTable.addFilter(this);
  }

  @Override
  public final SimpleColoredComponent getRenderer() {
    if (!hasFilter()) myTable.removeFilter(this);
    myLabel.clear();
    setLabel(myLabel);
    return myLabel;
  }

  protected abstract void setLabel(SimpleColoredComponent component);

  public abstract boolean hasFilter();

  public abstract void clearFilter();

  public abstract boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target);
}
