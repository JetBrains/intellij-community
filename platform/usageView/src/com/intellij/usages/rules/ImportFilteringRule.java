// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;

/**
 * This extension provides the ability to hide search results inside import statements from usage views and usage popups.
 * Extend this class and register the implementation as {@code com.intellij.importFilteringRule} extension in plugin.xml
 * to provide a way to determine whether a usage is in import.
 *
 * @see com.intellij.find.actions.FindUsagesAction
 * @see com.intellij.find.actions.ShowUsagesAction
 * @see com.intellij.refactoring.BaseRefactoringProcessor#showUsageView
 */
public abstract class ImportFilteringRule {

  public static final ExtensionPointName<ImportFilteringRule> EP_NAME = ExtensionPointName.create("com.intellij.importFilteringRule");

  /**
   * @param usage   a usage to test
   * @param targets array of targets for which the {@code usage} was discovered
   * @return {@code true} if the given {@code usage} is <b>not</b> inside an import,
   * or {@code false} if the given {@code usage} is inside an import and therefore should not be visible
   */
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    return isVisible(usage);
  }

  public boolean isVisible(@NotNull Usage usage) {
    throw new AbstractMethodError("isVisible(Usage) or isVisible(Usage, UsageTarget[]) must be implemented");
  }
}
