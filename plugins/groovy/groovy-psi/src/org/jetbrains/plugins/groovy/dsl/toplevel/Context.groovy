// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Scope

import static com.intellij.patterns.PlatformPatterns.psiFile
import static com.intellij.patterns.PlatformPatterns.virtualFile

@CompileStatic
class Context {
  private final List<ContextFilter> myFilters = []

  Context(Map args) {
    // Named parameter processing
    if (!args) return

    // filetypes : [<file_ext>*]
    List<String> extensions = args.filetypes as List<String>
    if (extensions != null) {
      extensions = extensions.collect { StringUtil.trimStart(it, '.') }
      def vfilePattern = extensions.size() == 1 ? virtualFile().withExtension(extensions[0]) : virtualFile().withExtension(extensions as String[])
      addFilter new FileContextFilter(psiFile().withVirtualFile(vfilePattern))
    }

    String scriptType = args.scriptType
    if (scriptType) {
      addFilter(new ScriptTypeFilter(scriptType))
    }

    String pathRegexp = args.pathRegexp
    if (pathRegexp) {
      addFilter new FileContextFilter(psiFile().withVirtualFile(virtualFile().withPath(StandardPatterns.string().matches(pathRegexp))))
    }

    // filter by scope first, then by ctype
    // scope: <scope>
    if (args.scope) {
      myFilters.addAll(((Scope) args.scope).createFilters(args))
    }

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.ctype instanceof String) {
      addFilter ClassContextFilter.subtypeOf(args.ctype as String)
    }
    else if (args.ctype instanceof ElementPattern) {
      addFilter ClassContextFilter.fromClassPattern(args.ctype as ElementPattern)
    }
  }

  private void addFilter(ContextFilter cl) {
    myFilters << cl
  }

  @NotNull
  ContextFilter getFilter() {
    if (myFilters.size() == 1) {
      return myFilters[0]
    }

    return CompositeContextFilter.compose(myFilters, true)
  }

}
