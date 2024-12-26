// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class ScriptTypeFilter implements ContextFilter {
  private final String myScriptType;

  public ScriptTypeFilter(String scriptType) {
    myScriptType = scriptType;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    PsiFile file = descriptor.getPlaceFile();
    if (file instanceof GroovyFile && ((GroovyFile)file).isScript()) {
      return myScriptType.contains(getScriptTypeId((GroovyFile)file));
    }
    return false;
  }

  private static @NotNull String getScriptTypeId(@NotNull GroovyFile script) {
    GroovyScriptType scriptType = GroovyScriptTypeDetector.getScriptType(script);
    return scriptType == null ? "default" : scriptType.getId();
  }
}
