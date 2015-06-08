package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchException;
import com.intellij.structuralsearch.StructuralSearchUtil;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Mossienko
 * Date: 11.06.2009
 * Time: 16:25:12
 */
public class ScriptSupport {
  private final Script script;

  public ScriptSupport(String text, String name) {
    File scriptFile = new File(text);
    GroovyShell shell = new GroovyShell();
    try {
      script = scriptFile.exists() ? shell.parse(scriptFile):shell.parse(text, name);
    } catch (Exception ex) {
      Logger.getInstance(getClass().getName()).error(ex);
      throw new RuntimeException(ex);
    }
  }

  public String evaluate(MatchResult result, PsiElement context) {
    try {
      final Binding binding = new Binding();

      if (result != null) {
        for(MatchResult r:result.getAllSons()) {
          if (r.isMultipleMatch()) {
            final ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
            for (MatchResult r2 : r.getAllSons()) {
              elements.add(StructuralSearchUtil.getParentIfIdentifier(r2.getMatch()));
            }
            binding.setVariable(r.getName(), elements);
          }
          else {
            binding.setVariable(r.getName(), StructuralSearchUtil.getParentIfIdentifier(r.getMatch()));
          }
        }
      }

      if (context == null) {
        context = result.getMatch();
      }
      context = StructuralSearchUtil.getParentIfIdentifier(context);
      binding.setVariable("__context__", context);
      script.setBinding(binding);

      Object o = script.run();
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
