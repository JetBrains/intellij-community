// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.psi.PsiPolyVariantReference;
import org.jetbrains.idea.devkit.inspections.UnresolvedPluginConfigReferenceInspection;

/**
 * Marker interface for resolve highlighting via {@link UnresolvedPluginConfigReferenceInspection}.
 * <p>
 * NOTE: While references may implement {@link PsiPolyVariantReference}, this should rarely be the case as
 * they usually must have unique declarations.
 * </p>
 */
public interface PluginConfigReference extends HighlightedReference, EmptyResolveMessageProvider {
}
