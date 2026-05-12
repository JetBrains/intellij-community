// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchScriptEngine;
import com.intellij.structuralsearch.StructuralSearchScriptException;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Internal
public final class ScriptSupport {
  private static final Logger LOG = Logger.getInstance(ScriptSupport.class);
  /**
   * Artificial filename without extension must be different from any variable name or the variable will get hidden by the script.
   * We use a randomly generated uuid for this, so the chance of accidental collision with an existing variable name is extremely small.
   * This also enables to filter out this uuid from Groovy error messages, to clarify for which SSR variable the script failed.
   */
  public static final @NlsSafe String UUID = "a3cd264774bf4efb9ab609b250c5165c";

  private final StructuralSearchScriptEngine.CompiledScript myScript;
  private final ScriptLog myScriptLog;
  private final String myName;
  private final Collection<String> myVariableNames;

  public ScriptSupport(Project project, StructuralSearchScriptEngine.CompiledScript script, String name, Collection<String> variableNames) {
    myScriptLog = new ScriptLog(project);
    myName = name;
    myVariableNames = variableNames;
    myScript = script;
  }

  private static void buildVariableMap(@NotNull MatchResult result, @NotNull Map<String, Object> out) {
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

      return myScript.evaluate(variableMap);
    }
    catch (ProcessCanceledException t) {
      throw t;
    }
    catch (Throwable t) {
      Logger.getInstance(ScriptSupport.class).info("Exception thrown by Structural Search script", t);
      throw new StructuralSearchScriptException(t);
    }
  }

  public static StructuralSearchScriptEngine.CompiledScript buildScript(
    @NotNull Project project,
    @NotNull String scriptName,
    @NotNull String scriptText,
    @NotNull MatchOptions matchOptions
  ) throws MalformedPatternException {
    final List<StructuralSearchScriptEngine> engines = StructuralSearchScriptEngine.EP_NAME.getExtensionList();
    if (engines.isEmpty()) {
      throw new MalformedPatternException(SSRBundle.message("error.groovy.script.engine.not.available"));
    }
    try {
      return engines.getFirst().compile(project, scriptName, scriptText, matchOptions);
    }
    catch (MalformedPatternException | ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.warn(e);
      throw new MalformedPatternException(SSRBundle.message("error.in.groovy.parser"));
    }
  }
}
