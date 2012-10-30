package com.intellij.appengine.enhancement;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import org.jetbrains.jps.appengine.build.EnhancerProcessHandlerBase;

/**
 * @author nik
 */
public class EnhancerProcessHandler extends EnhancerProcessHandlerBase {
  private final CompileContext myContext;

  public EnhancerProcessHandler(final Process process, final String commandLine, CompileContext context) {
    super(process, commandLine, null);
    myContext = context;
  }

  @Override
  protected void reportInfo(String message) {
    myContext.addMessage(CompilerMessageCategory.INFORMATION, message, null, -1, -1);
  }

  @Override
  protected void reportError(String message) {
    myContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
  }
}
