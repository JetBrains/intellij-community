// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author sergey.evdokimov
 */
final class DefaultGroovyFrameworkConfigNotification extends GroovyFrameworkConfigNotification {

  @Override
  public boolean hasFrameworkStructure(@NotNull Module module) {
    return true;
  }

  @Override
  public boolean hasFrameworkLibrary(@NotNull Module module) {
    return JavaPsiFacade.getInstance(module.getProject()).findClass(
      GroovyCommonClassNames.GROOVY_OBJECT, module.getModuleWithDependenciesAndLibrariesScope(true)
    ) != null;
  }
}
