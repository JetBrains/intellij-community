// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.resolve.imports.*;

import java.util.Collection;
import java.util.List;

@Deprecated
public interface GrImportContributor extends org.jetbrains.plugins.groovy.lang.resolve.imports.GrImportContributor {

  @NotNull
  @Override
  default List<GroovyImport> getFileImports(@NotNull GroovyFile file) {
    return ContainerUtil.mapNotNull(getImports(file), it -> {
      switch (it.getType()) {
        case REGULAR:
          return new RegularImport(it.getName());
        case STATIC:
          String classFqn = StringUtil.getPackageName(it.getName());
          String memberName = StringUtil.getShortName(it.getName());
          return new StaticImport(classFqn, memberName);
        case STAR:
          return new StarImport(it.getName());
        case STATIC_STAR:
          return new StaticStarImport(it.getName());
        default:
          return null;
      }
    });
  }

  @NotNull
  Collection<Import> getImports(@NotNull GroovyFile file);
}
