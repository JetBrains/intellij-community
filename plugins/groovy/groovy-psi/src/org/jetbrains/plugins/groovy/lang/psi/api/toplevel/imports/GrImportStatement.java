// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports;

import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport;

/**
 * @author ilyas
 */
public interface GrImportStatement extends GrTopStatement {
  GrImportStatement[] EMPTY_ARRAY = new GrImportStatement[0];

  ArrayFactory<GrImportStatement> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrImportStatement[count];

  @Nullable
  GrCodeReferenceElement getImportReference();

  @Nullable
  String getImportedName();

  boolean isOnDemand();

  boolean isStatic();

  boolean isAliasedImport();

  @NotNull
  GrModifierList getAnnotationList();

  @Nullable
  PsiClass resolveTargetClass();

  @Nullable
  GrImportAlias getAlias();

  @Nullable
  GroovyImport getImport();
}
