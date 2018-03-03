// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchException;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class ScriptSupport {
  /**
   * Artificial filename without extension must be different from any variable name or the variable will get hidden by the script.
   * We use a randomly generated uuid for this, so the chance of accidental collision with an existing variable name is extremely small.
   * This also enables to filter out this uuid from Groovy error messages, to clarify for which SSR variable the script failed.
   */
  private static final String UUID = "a3cd264774bf4efb9ab609b250c5165c";

  private final Script script;
  private final ScriptLog myScriptLog;
  private final String myName;

  public ScriptSupport(Project project, String text, String name) {
    myScriptLog = new ScriptLog(project);
    myName = name;
    final File scriptFile = new File(text);
    final GroovyShell shell = new GroovyShell();
    try {
      script = scriptFile.exists() ? shell.parse(scriptFile) : shell.parse(text, name + UUID + ".groovy");
    } catch (Exception ex) {
      Logger.getInstance(getClass().getName()).error(ex);
      throw new RuntimeException(ex);
    }
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
        @SuppressWarnings("unchecked")
        final List<PsiElement> list = (List<PsiElement>)value;
        list.add(match);
      }
      else if (value instanceof PsiElement){
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

  public String evaluate(MatchResult result, PsiElement context) {
    try {
      final HashMap<String, Object> variableMap = new HashMap<>();
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

      script.setBinding(new Binding(variableMap));

      final Object o = script.run();
      return String.valueOf(o);
    } catch (GroovyRuntimeException ex) {
      throw new StructuralSearchException(SSRBundle.message("groovy.script.error", StringUtil.replace(ex.getMessage(), UUID, "")));
    } finally {
      script.setBinding(null);
    }
  }

  public static String checkValidScript(String scriptText) {
    try {
      final File scriptFile = new File(scriptText);
      final GroovyShell shell = new GroovyShell();
      final Script script = scriptFile.exists() ? shell.parse(scriptFile) : shell.parse(scriptText);
      return null;
    } catch (IOException e) {
      return e.getMessage();
    } catch (MultipleCompilationErrorsException e) {
      final ErrorCollector errorCollector = e.getErrorCollector();
      @SuppressWarnings("unchecked") final List<Message> errors = errorCollector.getErrors();
      for (Message error : errors) {
        if (error instanceof SyntaxErrorMessage) {
          final SyntaxErrorMessage errorMessage = (SyntaxErrorMessage)error;
          final SyntaxException cause = errorMessage.getCause();
          return cause.getMessage();
        }
      }
      return e.getMessage();
    } catch (CompilationFailedException ex) {
      return ex.getLocalizedMessage();
    }
  }
}
