// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.rename.ModPlanRenameAliasFilter;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A reference from Groovy comes through an import alias when its name is not the name of the renamed Java class. The legacy rename binds
 * the reference to the renamed class, and the bind keeps it, as it still resolves to the class.
 * <pre>{@code
 * import p.Widget as W
 * print new W()
 * // rename the Java class Widget to Gadget
 * import p.Gadget as W
 * print new W()
 * }</pre>
 */
@ApiStatus.Internal
public final class GroovyRenameAliasFilter implements ModPlanRenameAliasFilter {
  @Override
  public boolean isAliasReference(@NotNull UsageInfo usage, @NotNull PsiClass renamedClass) {
    PsiReference reference = usage.getReference();
    if (usage.isNonCodeUsage || reference == null || !ModPlanGroovyRenameProvider.isGroovy(reference.getElement())) return false;
    String text = reference.getRangeInElement().substring(reference.getElement().getText());
    String shortName = StringUtil.getShortName(StringUtil.getShortName(text), '$');
    return !shortName.equals(renamedClass.getName());
  }
}
