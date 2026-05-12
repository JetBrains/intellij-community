// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.groovy;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchScriptEngine;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class GroovyStructuralSearchScriptEngine implements StructuralSearchScriptEngine {
  private static final Logger LOG = Logger.getInstance(GroovyStructuralSearchScriptEngine.class);

  @Override
  public @NotNull CompiledScript compile(@NotNull Project project,
                                         @NotNull String scriptName,
                                         @NotNull String scriptText,
                                         @NotNull MatchOptions matchOptions) throws MalformedPatternException {
    try {
      final GroovyShell shell = new GroovyShell(Objects.requireNonNull(matchOptions.getDialect()).getClass().getClassLoader());
      return new GroovyCompiledScript(shell.parse(scriptText, scriptName + ScriptSupport.UUID + ".groovy"));
    }
    catch (CompilationFailedException e) {
      throw new MalformedPatternException(getCompilationErrorMessage(e));
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.warn(e);
      throw new MalformedPatternException(SSRBundle.message("error.in.groovy.parser"));
    }
  }

  private static String getCompilationErrorMessage(@NotNull CompilationFailedException cause) {
    final String syntaxErrorMessage = getSyntaxErrorMessage(cause);
    return syntaxErrorMessage != null ? syntaxErrorMessage : cause.getLocalizedMessage();
  }

  private static @Nullable String getSyntaxErrorMessage(@NotNull CompilationFailedException cause) {
    if (!(cause instanceof MultipleCompilationErrorsException e)) {
      return null;
    }
    for (Object error : e.getErrorCollector().getErrors()) {
      if (error instanceof SyntaxErrorMessage syntaxErrorMessage) {
        return syntaxErrorMessage.getCause().getLocalizedMessage();
      }
    }
    return null;
  }

  private static final class GroovyCompiledScript implements CompiledScript {
    private final Script myScript;

    private GroovyCompiledScript(@NotNull Script script) {
      myScript = script;
    }

    @Override
    public Object evaluate(@NotNull Map<String, Object> variables) {
      myScript.setBinding(new Binding(variables));
      try {
        return myScript.run();
      }
      finally {
        myScript.setBinding(null);
      }
    }
  }
}
