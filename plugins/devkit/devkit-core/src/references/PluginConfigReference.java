// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.psi.PsiReference;
import org.jetbrains.idea.devkit.inspections.UnresolvedPluginConfigReferenceInspection;

/**
 * Marker interface for highlighting via {@link UnresolvedPluginConfigReferenceInspection}.
 */
public interface PluginConfigReference extends PsiReference, EmptyResolveMessageProvider {
}
