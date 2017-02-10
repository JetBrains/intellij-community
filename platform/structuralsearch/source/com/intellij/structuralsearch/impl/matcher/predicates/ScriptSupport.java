package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 * Date: 11.06.2009
 * Time: 16:25:12
 */
public class ScriptSupport {
  private final Script script;
  private final ScriptLog myScriptLog;
  private final String myName;

  public ScriptSupport(Project project, String text, String name) {
    myScriptLog = new ScriptLog(project);
    myName = name;
    File scriptFile = new File(text);
    GroovyShell shell = new GroovyShell();
    try {
      script = scriptFile.exists() ? shell.parse(scriptFile) : shell.parse(text, name + "_script.groovy");
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
    if (result.hasSons()) {
      for (MatchResult son : result.getAllSons()) {
        buildVariableMap(son, out);
      }
    }
    return out;
  }

  public String evaluate(MatchResult result, PsiElement context) {
    try {
      final HashMap<String, Object> variableMap = new HashMap<>();
      variableMap.put(ScriptLog.SCRIPT_LOG_VAR_NAME, myScriptLog);
      if (result != null) {
        buildVariableMap(result, variableMap);
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
      throw new StructuralSearchException(SSRBundle.message("groovy.script.error", ex.getMessage()));
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
      final List<Message> errors = errorCollector.getErrors();
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
