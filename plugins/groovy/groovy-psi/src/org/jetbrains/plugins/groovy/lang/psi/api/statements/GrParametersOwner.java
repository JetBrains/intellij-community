// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * Marker interface for computing
 * {@link org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter#getDeclarationScope GrParameter#getDeclarationScope}
 */
public interface GrParametersOwner extends GroovyPsiElement {
}
