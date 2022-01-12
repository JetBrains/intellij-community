// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Classes extending this class define filter user interfaces elements in the Structural Search dialog.
 * For example the count filter or text filter, which can be added to $variables$ in the template.
 *
 * @author Bas Leijdekkers
 */
public abstract class ModifierAction extends DumbAwareAction implements Modifier {

  /**
   * This extension points causes memory leaks and other problems, because FilterActions have state.
   * @deprecated Please use {@link ModifierProvider} instead.
   */
  @Deprecated(forRemoval = true)
  public static final ExtensionPointName<ModifierAction> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.filter");

  private static final AtomicInteger myFilterCount = new AtomicInteger();

  protected final SimpleColoredComponent myLabel = new SimpleColoredComponent();
  protected ModifierTable myTable;
  private final int myPosition;

  private boolean myApplicable = true;
  private boolean myActive = false;

  protected ModifierAction(@NotNull Supplier<String> text) {
    super(text);
    myPosition = myFilterCount.incrementAndGet();
  }

  public void setTable(@NotNull ModifierTable table) {
    myTable = table;
  }

  @Override
  public final int position() {
    return myPosition;
  }

  /**
   * The text displayed in the template editor inlays for this filter.
   * @param variable  variable from which the value of the filter text to be displayed can be retrieved.
   * @return a short text to display as editor inlay.
   */
  @NotNull
  public String getShortText(NamedScriptableDefinition variable) {
    return "";
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    initModifier();
    myApplicable = false;
    myActive = true;
    myTable.addModifier(this);
  }

  @Override
  public final SimpleColoredComponent getRenderer() {
    myLabel.clear();
    setLabel(myLabel);
    return myLabel;
  }

  protected abstract void setLabel(SimpleColoredComponent component);

  public abstract boolean hasModifier();

  final boolean isActive() {
    if (hasModifier()) {
      myActive = true;
    }
    return myActive;
  }

  protected void initModifier() {}

  public abstract void clearModifier();

  final void reset() {
    myApplicable = true;
    myActive = false;
  }

  protected abstract boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target);

  protected final boolean isApplicableConstraint(@NonNls String constraintName,
                                                 List<? extends PsiElement> nodes,
                                                 boolean completePattern,
                                                 boolean target) {
    final StructuralSearchProfile profile = myTable.getProfile();
    return profile != null && profile.isApplicableConstraint(constraintName, nodes, completePattern, target);
  }

  final boolean isAvailable() {
    return myApplicable && !isActive();
  }

  final boolean checkApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    return myApplicable = isApplicable(nodes, completePattern, target);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(myApplicable && !isActive());
  }
}
