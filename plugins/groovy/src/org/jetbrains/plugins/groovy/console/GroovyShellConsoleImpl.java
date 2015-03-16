package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Groovy language console for Groovy Shell action
 */
public class GroovyShellConsoleImpl extends GroovyConsoleImpl {

  public GroovyShellConsoleImpl(Project project, String name) {
    super(project, name);
  }

  @Override
  protected boolean isShell() {
    return true;
  }

  @NotNull
  @Override
  protected String addToHistoryInner(@NotNull TextRange textRange, @NotNull EditorEx editor, boolean erase, boolean preserveMarkup) {
    final String result = super.addToHistoryInner(textRange, editor, erase, preserveMarkup);

    if ("purge variables".equals(result.trim())) {
      clearVariables();
    }
    else if ("purge classes".equals(result.trim())) {
      clearClasses();
    }
    else if ("purge imports".equals(result.trim())) {
      clearImports();
    }
    else if ("purge all".equals(result.trim())) {
      clearVariables();
      clearClasses();
      clearImports();
    }
    else {
      processCode();
    }

    return result;
  }

  private void clearVariables() {
    getFile().clearVariables();
  }

  private void clearClasses() {
    getFile().clearClasses();
  }

  private void clearImports() {
    getFile().clearImports();
  }
}
