package com.intellij.execution.junit2;

import com.intellij.execution.junit2.segments.InputConsumer;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.junit2.segments.PacketConsumer;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;

public class InputRouter implements PacketConsumer {
  private InputConsumer myInputConsumer = InputConsumer.DEAF;
  private static final Condition<ConsoleViewContentType> ANY_EXCEPT_ERR = new Condition<ConsoleViewContentType>() {
    public boolean value(final ConsoleViewContentType consoleViewContentType) {
      return consoleViewContentType != ConsoleViewContentType.ERROR_OUTPUT;
    }
  };
  private static final Condition<ConsoleViewContentType> ERR = Conditions.not(ANY_EXCEPT_ERR);
  private final Condition<ConsoleViewContentType> mySourceFilter;

  private InputRouter(final Condition<ConsoleViewContentType> sourceFilter) {
    mySourceFilter = sourceFilter;
  }

  public void readPacketFrom(final ObjectReader reader) {
    myInputConsumer = reader.readObject();
  }

  public void setInputConsumer(final InputConsumer inputConsumer) {
    myInputConsumer = inputConsumer;
  }

  public void onFinished() {
  }

  public String getPrefix() {
    return PoolOfDelimiters.INPUT_COSUMER;
  }

  public void attachTo(final ProcessHandler process) {
    process.addProcessListener(new MyProcessAdapter(mySourceFilter));
  }

  public static InputRouter createOutRouter() {
    return new InputRouter(ANY_EXCEPT_ERR);
  }

  public static InputRouter createErrRouter() {
    return new InputRouter(ERR);
  }

  private class MyProcessAdapter extends ProcessAdapter {
    private final Condition<ConsoleViewContentType> mySourceFilter;

    public MyProcessAdapter(final Condition<ConsoleViewContentType> sourceFilter) {
      mySourceFilter = sourceFilter;
    }

    public void processTerminated(ProcessEvent event) {
      final ProcessHandler processHandler = event.getProcessHandler();
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          processHandler.removeProcessListener(MyProcessAdapter.this);
        }
      });
    }

    public void onTextAvailable(final ProcessEvent event, final Key outputType) {
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          final ConsoleViewContentType consoleViewType = ConsoleViewContentType.getConsoleViewType(outputType);
          if (mySourceFilter.value(consoleViewType)) {
            myInputConsumer.onOutput(event.getText(), consoleViewType);
          }
        }
      });
    }
  }
}
