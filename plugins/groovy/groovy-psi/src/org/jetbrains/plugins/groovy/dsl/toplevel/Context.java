// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.VirtualFilePattern;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class Context {
  public Context(@SuppressWarnings("rawtypes") Map args) {
    // Named parameter processing
    if (args == null) return;

    // filetypes : [<file_ext>*]
    @SuppressWarnings("unchecked")
    List<String> extensions = (List<String>)args.get("filetypes");
    if (extensions != null) {
      extensions = ContainerUtil.map(extensions, x -> StringUtil.trimStart(x, "."));
      VirtualFilePattern vfilePattern = extensions.size() == 1
                                        ? PlatformPatterns.virtualFile().withExtension(extensions.get(0))
                                        : PlatformPatterns.virtualFile()
                                          .withExtension(ArrayUtil.toStringArray(extensions));
      addFilter(new FileContextFilter(PlatformPatterns.psiFile().withVirtualFile(vfilePattern)));
    }

    if (args.containsKey("scriptType")) {
      addFilter(new ScriptTypeFilter((String)args.get("scriptType")));
    }

    if (args.containsKey("pathRegexp")) {
      addFilter(new FileContextFilter(PlatformPatterns.psiFile().withVirtualFile(
        PlatformPatterns.virtualFile().withPath(StandardPatterns.string().matches((String)args.get("pathRegexp"))))));
    }

    // filter by scope first, then by ctype
    // scope: <scope>
    if (args.get("scope") != null) {
      myFilters.addAll(((Scope)args.get("scope")).createFilters(args));
    }

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.get("ctype") instanceof String) {
      addFilter(ClassContextFilter.subtypeOf((String)args.get("ctype")));
    }
    else if (args.get("ctype") instanceof ElementPattern) {
      addFilter(ClassContextFilter.fromClassPattern((ElementPattern)args.get("ctype")));
    }
  }

  private void addFilter(ContextFilter cl) {
    myFilters.add(cl);
  }

  public @NotNull ContextFilter getFilter() {
    if (myFilters.size() == 1) {
      return myFilters.get(0);
    }


    return CompositeContextFilter.compose(myFilters, true);
  }

  private final List<ContextFilter> myFilters = new ArrayList<>();
}
