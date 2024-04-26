// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel.scopes;

import com.intellij.patterns.*;
import com.intellij.psi.SyntheticElement;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.FileContextFilter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unused"})
public class ScriptScope extends Scope {
  public ScriptScope(Map args) {
    namePattern = args == null ? null : (String)args.get("name");
    extension = args == null ? null : (String)args.get("extension");
  }

  @Override
  public List<ContextFilter> createFilters(Map args) {
    List<ContextFilter> result = new ArrayList<>();
    PsiFilePattern.Capture<GroovyFile> scriptPattern = GroovyPatterns.groovyScript();
    if (extension != null && !extension.isEmpty()) {
      scriptPattern = scriptPattern.withVirtualFile(PlatformPatterns.virtualFile().withExtension(extension));
    }

    result.add(new FileContextFilter(scriptPattern));

    // Name matcher
    if (namePattern != null && !namePattern.isEmpty()) {
      result.add(new FileContextFilter(
        PlatformPatterns.psiFile().withName(StandardPatterns.string().matches(namePattern))));
    }

    // Process unqualified references only
    String ctype = (String)args.get("ctype");
    if (ctype == null || ctype.isEmpty()) {
      ElementPattern synt = StandardPatterns.instanceOf(SyntheticElement.class);

      PsiClassPattern psiClass = PsiJavaPatterns.psiClass();
      ElementPattern pattern = doAnd(psiClass, synt);
      result.add(ClassContextFilter.fromClassPattern(pattern));
    }

    return result;
  }

  private static PsiClassPattern doAnd(PsiClassPattern psiClass, ElementPattern synt) {
    return psiClass.and(synt);
  }

  public final String getNamePattern() {
    return namePattern;
  }

  public final String getExtension() {
    return extension;
  }

  private final String namePattern;
  private final String extension;
}
