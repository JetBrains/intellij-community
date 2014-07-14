/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Scope
import static com.intellij.patterns.PlatformPatterns.psiFile
import static com.intellij.patterns.PlatformPatterns.virtualFile

/**
 * @author ilyas
 */
class Context {
  private final List<ContextFilter> myFilters = []

  public Context(Map args) {
    // Named parameter processing
    if (!args) return

    // filetypes : [<file_ext>*]
    List<String> extensions = args.filetypes
    if (extensions instanceof List) {
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
      addFilter ClassContextFilter.subtypeOf(args.ctype)
    }
    else if (args.ctype instanceof ElementPattern) {
      addFilter ClassContextFilter.fromClassPattern(args.ctype)
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
