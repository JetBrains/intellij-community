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
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
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
      final ClassLoader dialectClassLoader = Objects.requireNonNull(matchOptions.getDialect()).getClass().getClassLoader();
      final GroovyShell shell = createGroovyShell(dialectClassLoader);
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

  @VisibleForTesting
  public static @NotNull GroovyShell createGroovyShell(@Nullable ClassLoader dialectClassLoader) {
    ClassLoader engineClassLoader = GroovyStructuralSearchScriptEngine.class.getClassLoader();
    if (dialectClassLoader == null || dialectClassLoader == engineClassLoader) {
      return new GroovyShell(engineClassLoader);
    }
    return new GroovyShell(new StructuralSearchScriptClassLoader(engineClassLoader, dialectClassLoader));
  }

  private static final class StructuralSearchScriptClassLoader extends ClassLoader {
    private final @NotNull ClassLoader myPrimary;
    private final @NotNull ClassLoader mySecondary;

    private StructuralSearchScriptClassLoader(@NotNull ClassLoader primary, @NotNull ClassLoader secondary) {
      super(null);
      myPrimary = primary;
      mySecondary = secondary;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
          loadedClass = loadClass(name, myPrimary, mySecondary);
        }
        if (resolve) {
          resolveClass(loadedClass);
        }
        return loadedClass;
      }
    }

    @Override
    public URL getResource(String name) {
      URL resource = myPrimary.getResource(name);
      return resource == null ? mySecondary.getResource(name) : resource;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      LinkedHashSet<URL> resources = new LinkedHashSet<>();
      resources.addAll(Collections.list(myPrimary.getResources(name)));
      resources.addAll(Collections.list(mySecondary.getResources(name)));
      return Collections.enumeration(resources);
    }

    private static @NotNull Class<?> loadClass(@NotNull String name,
                                               @NotNull ClassLoader primary,
                                               @NotNull ClassLoader secondary) throws ClassNotFoundException {
      try {
        return primary.loadClass(name);
      }
      catch (ClassNotFoundException ignored) {
        return secondary.loadClass(name);
      }
    }
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
