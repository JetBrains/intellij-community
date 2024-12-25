// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class ScriptSupport {

  private static final Logger LOG = Logger.getInstance(ScriptSupport.class);
  /**
   * Artificial filename without extension must be different from any variable name or the variable will get hidden by the script.
   * We use a randomly generated uuid for this, so the chance of accidental collision with an existing variable name is extremely small.
   * This also enables to filter out this uuid from Groovy error messages, to clarify for which SSR variable the script failed.
   */
  public static final @NlsSafe String UUID = "a3cd264774bf4efb9ab609b250c5165c";

  private final Script myScript;
  private final ScriptLog myScriptLog;
  private final String myName;
  private final Collection<String> myVariableNames;

  public ScriptSupport(Project project, Script script, String name, Collection<String> variableNames) {
    myScriptLog = new ScriptLog(project);
    myName = name;
    myVariableNames = variableNames;
    myScript = script;
  }

  private static Map<String, Object> buildVariableMap(@NotNull MatchResult result, @NotNull Map<String, Object> out) {
    final String name = result.getName();
    if (name != null && !result.isMultipleMatch()) {
      final Object value = out.get(name);
      final PsiElement match = StructuralSearchUtil.getPresentableElement(result.getMatch());
      if (value == null) {
        out.put(name, match);
      }
      else if (value instanceof List) {
        @SuppressWarnings("unchecked") final List<PsiElement> list = (List<PsiElement>)value;
        list.add(match);
      }
      else if (value instanceof PsiElement) {
        final List<PsiElement> list = new ArrayList<>();
        list.add((PsiElement)value);
        list.add(match);
        out.put(name, list);
      }
      else {
        throw new AssertionError();
      }
    }
    for (MatchResult son : result.getChildren()) {
      buildVariableMap(son, out);
    }
    return out;
  }

  public Object evaluate(MatchResult result, PsiElement context) {
    try {
      Map<String, Object> variableMap = new HashMap<>();
      myVariableNames.forEach(n -> variableMap.put(n, null));
      variableMap.put(ScriptLog.SCRIPT_LOG_VAR_NAME, myScriptLog);
      if (result != null) {
        buildVariableMap(result.getRoot(), variableMap);
        if (context == null) {
          context = result.getMatch();
        }
      }

      context = StructuralSearchUtil.getPresentableElement(context);
      variableMap.put(myName, context);
      variableMap.put(Configuration.CONTEXT_VAR_NAME, context);

      myScript.setBinding(new Binding(variableMap));

      return myScript.run();
    }
    catch (ThreadDeath | ProcessCanceledException t) {
      throw t;
    }
    catch (Throwable t) {
      Logger.getInstance(ScriptSupport.class).info("Exception thrown by Structural Search Groovy Script", t);
      throw new StructuralSearchScriptException(t);
    }
    finally {
      myScript.setBinding(null);
    }
  }

  public static Script buildScript(
    @NotNull String scriptName,
    @NotNull String scriptText,
    @NotNull MatchOptions matchOptions
  ) throws MalformedPatternException {
    try {
      final GroovyShell shell = new GroovyShell(Objects.requireNonNull(matchOptions.getDialect()).getClass().getClassLoader());
      return shell.parse(scriptText, scriptName + UUID + ".groovy");
    }
    catch (MultipleCompilationErrorsException e) {
      final ErrorCollector errorCollector = e.getErrorCollector();
      final List<? extends Message> errors = errorCollector.getErrors();
      for (Message error : errors) {
        if (error instanceof SyntaxErrorMessage syntaxError) {
          final SyntaxException cause = syntaxError.getCause();
          throw new MalformedPatternException(cause.getLocalizedMessage());
        }
      }
      throw new MalformedPatternException(e.getLocalizedMessage());
    }
    catch (CompilationFailedException ex) {
      throw new MalformedPatternException(ex.getLocalizedMessage());
    }
    catch (Throwable e) {
      // to catch errors in groovy parsing
      LOG.warn(e);
      throw new MalformedPatternException(SSRBundle.message("error.in.groovy.parser"));
    }
  }
}
