package com.intellij.appengine.enhancement;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.util.containers.FactoryMap;

/**
 * @author nik
 */
public class EnhancerProcessHandler extends OSProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.enhancement.EnhancerProcessHandler");
  private FactoryMap<Key, EnhancerOutputParser> myParsers = new FactoryMap<Key, EnhancerOutputParser>() {
    @Override
    protected EnhancerOutputParser create(Key key) {
      return new EnhancerOutputParser(ProcessOutputTypes.STDERR.equals(key));
    }
  };
  private final CompileContext myContext;

  public EnhancerProcessHandler(final Process process, final String commandLine, CompileContext context) {
    super(process, commandLine);
    myContext = context;
  }

  @Override
  public void notifyTextAvailable(String text, Key outputType) {
    super.notifyTextAvailable(text, outputType);
    myParsers.get(outputType).appendText(text);
  }

  private class EnhancerOutputParser {
    private StringBuilder myBuffer = new StringBuilder();
    private final boolean myErrorStream;

    public EnhancerOutputParser(boolean errorStream) {
      myErrorStream = errorStream;
    }


    public void appendText(String text) {
      myBuffer.append(text);
      int start = 0;
      while (true) {
        int lineEnd1 = myBuffer.indexOf("\n", start);
        int lineEnd2 = myBuffer.indexOf("\r", start);
        if (lineEnd1 == -1 && lineEnd2 == -1) break;

        int lineEnd = lineEnd1 == -1 ? lineEnd2 : lineEnd2 == -1 ? lineEnd1 : Math.min(lineEnd1, lineEnd2);
        parseLine(myBuffer.substring(start, lineEnd).trim());
        start = lineEnd + 1;
      }

      myBuffer.delete(0, start);
    }

    private void parseLine(String line) {
      LOG.debug(myErrorStream ? "[err] " + line : line);
      if (myErrorStream) {
        myContext.addMessage(CompilerMessageCategory.ERROR, line, null, -1, -1);
        return;
      }

      if (line.startsWith("DataNucleus Enhancer completed")) {
        myContext.addMessage(CompilerMessageCategory.INFORMATION, line, null, -1, -1);
      }
    }
  }
}
