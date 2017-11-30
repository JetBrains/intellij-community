// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.List;

public interface GrImportContributor {

  ExtensionPointName<GrImportContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.importContributor");

  @NotNull
  List<GroovyImport> getFileImports(@NotNull GroovyFile file);
}
