// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalExecutionConsole implements ConsoleView, ObservableConsoleView {
  private static final Logger LOG = Logger.getInstance(TerminalExecutionConsole.class);

  private JBTerminalWidget myTerminalWidget;
  private final Project myProject;
  private final AppendableTerminalDataStream myDataStream;
  private final AtomicBoolean myAttachedToProcess = new AtomicBoolean(false);
  private volatile boolean myLastCR = false;
  private final PendingTasksRunner myOnResizedRunner;
  private final TerminalConsoleContentHelper myContentHelper = new TerminalConsoleContentHelper(this);

  private boolean myEnterKeyDefaultCodeEnabled = true;

  public TerminalExecutionConsole(@NotNull Project project, @Nullable ProcessHandler processHandler) {
    myProject = project;
    myOnResizedRunner = new PendingTasksRunner(2000, project);
    JBTerminalSystemSettingsProviderBase provider = new JBTerminalSystemSettingsProviderBase() {
      @Override
      public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
        return HyperlinkStyle.HighlightMode.ALWAYS;
      }
    };
    myDataStream = new AppendableTerminalDataStream();
    myTerminalWidget = new ConsoleTerminalWidget(project, provider);
    Disposer.register(myTerminalWidget, provider);
    if (processHandler != null) {
      attachToProcess(processHandler);
    }
  }

  private void printText(@NotNull String text, @Nullable ConsoleViewContentType contentType) throws IOException {
    Color foregroundColor = contentType != null ? contentType.getAttributes().getForegroundColor() : null;
    if (foregroundColor != null) {
      myDataStream.append(encodeColor(foregroundColor));
    }

    myDataStream.append(text);

    if (foregroundColor != null) {
      myDataStream.append((char)CharUtils.ESC + "[39m"); //restore default foreground color
    }
    myContentHelper.onContentTypePrinted(ObjectUtils.notNull(contentType, ConsoleViewContentType.NORMAL_OUTPUT));
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    myContentHelper.addChangeListener(listener, parent);
  }

  @NotNull
  private static String encodeColor(@NotNull Color color) {
    return ((char)CharUtils.ESC) + "[" + "38;2;" + color.getRed() + ";" + color.getGreen() + ";" +
           color.getBlue() + "m";
  }

  /**
   * @deprecated use {@link #withEnterKeyDefaultCodeEnabled(boolean)}
   */
  @Deprecated
  public void setAutoNewLineMode(@SuppressWarnings("unused") boolean enabled) {
  }

  @NotNull
  public TerminalExecutionConsole withEnterKeyDefaultCodeEnabled(boolean enterKeyDefaultCodeEnabled) {
    myEnterKeyDefaultCodeEnabled = enterKeyDefaultCodeEnabled;
    return this;
  }

  /**
   * @deprecated use {{@link #addMessageFilter(Filter)}} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public void addMessageFilter(Project project, Filter filter) {
    myTerminalWidget.addMessageFilter(filter);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    // Convert line separators to CRLF to behave like ConsoleViewImpl.
    // For example, stacktraces passed to com.intellij.execution.testframework.sm.runner.SMTestProxy.setTestFailed have
    // only LF line separators on Unix.
    String textCRLF = convertTextToCRLF(text);
    try {
      printText(textCRLF, contentType);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private String convertTextToCRLF(@NotNull String text) {
    if (text.isEmpty()) return text;
    // Handle the case when \r and \n are in different chunks: "text1 \r" and "\n text2"
    boolean preserveFirstLF = text.startsWith(LineSeparator.LF.getSeparatorString()) && myLastCR;
    boolean preserveLastCR = text.endsWith(LineSeparator.CR.getSeparatorString());
    myLastCR = preserveLastCR;
    String textToConvert = text.substring(preserveFirstLF ? 1 : 0, preserveLastCR ? text.length() - 1 : text.length());
    String textCRLF = StringUtil.convertLineSeparators(textToConvert, LineSeparator.CRLF.getSeparatorString());
    if (preserveFirstLF) {
      textCRLF = LineSeparator.LF.getSeparatorString() + textCRLF;
    }
    if (preserveLastCR) {
      textCRLF += LineSeparator.CR.getSeparatorString();
    }
    return textCRLF;
  }

  /**
   * Clears history and screen buffers, positions the cursor at the top left corner.
   */
  @Override
  public void clear() {
    myLastCR = false;
    myTerminalWidget.getTerminalPanel().clearBuffer();
  }

  @Override
  public void scrollTo(int offset) {
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    if (processHandler != null) {
      attachToProcess(processHandler, true);
    }
  }

  /**
   * @param processHandler        ProcessHandler instance wrapping underlying PtyProcess
   * @param attachToProcessOutput true if process output should be printed in the console,
   *                              false if output printing is managed externally, e.g. by testing
   *                              console {@link com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView}
   */
  protected final void attachToProcess(@NotNull ProcessHandler processHandler, boolean attachToProcessOutput) {
    if (!myAttachedToProcess.compareAndSet(false, true)) {
      return;
    }
    myTerminalWidget.createTerminalSession(new ProcessHandlerTtyConnector(
      processHandler, EncodingProjectManager.getInstance(myProject).getDefaultCharset())
    );
    myTerminalWidget.start();

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (attachToProcessOutput) {
          myOnResizedRunner.execute(() -> {
            try {
              ConsoleViewContentType contentType = null;
              if (outputType != ProcessOutputTypes.STDOUT) {
                contentType = ConsoleViewContentType.getConsoleViewType(outputType);
              }

              String text = event.getText();
              if (outputType == ProcessOutputTypes.SYSTEM) {
                text = StringUtil.convertLineSeparators(text, LineSeparator.CRLF.getSeparatorString());
              }
              printText(text, contentType);
            }
            catch (IOException e) {
              LOG.info(e);
            }
          });
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          JBTerminalWidget widget = myTerminalWidget;
          if (widget != null) {
            widget.getTerminalPanel().setCursorVisible(false);
          }
          myAttachedToProcess.set(false);
        }, ModalityState.any());
      }
    });
  }

  @Override
  public void setOutputPaused(boolean value) {

  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    myTerminalWidget.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {

  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  /**
   * @deprecated already handled by {@link com.intellij.execution.runners.RunContentBuilder#createDescriptor()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public AnAction @NotNull [] detachConsoleActions(boolean prependSeparatorIfNonEmpty) {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public AnAction @NotNull [] createConsoleActions() {
    return new AnAction[]{new ScrollToTheEndAction(), new ClearAction()};
  }

  @Override
  public void allowHeavyFilters() {
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public void dispose() {
    myTerminalWidget = null;
  }

  public static boolean isAcceptable(@NotNull ProcessHandler processHandler) {
    return processHandler instanceof OSProcessHandler &&
           ((OSProcessHandler)processHandler).getProcess() instanceof PtyProcess &&
           !(processHandler instanceof ColoredProcessHandler);
  }

  private class ConsoleTerminalWidget extends JBTerminalWidget implements DataProvider {
    private ConsoleTerminalWidget(@NotNull Project project, @NotNull JBTerminalSystemSettingsProviderBase provider) {
      super(project, 200, 24, provider, TerminalExecutionConsole.this, TerminalExecutionConsole.this);
    }

    @Override
    protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                  @NotNull StyleState styleState,
                                                  @NotNull TerminalTextBuffer textBuffer) {
      JBTerminalPanel panel = new JBTerminalPanel((JBTerminalSystemSettingsProviderBase)settingsProvider, textBuffer, styleState) {
        @Override
        public Dimension requestResize(Dimension newSize,
                                       RequestOrigin origin,
                                       int cursorX,
                                       int cursorY,
                                       JediTerminal.ResizeHandler resizeHandler) {
          Dimension dimension = super.requestResize(newSize, origin, cursorX, cursorY, resizeHandler);
          myOnResizedRunner.setReady();
          return dimension;
        }

        @Override
        public void clearBuffer() {
          super.clearBuffer(false);
        }
      };

      Disposer.register(this, panel);
      return panel;
    }

    @Override
    protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
      return new TerminalStarter(terminal, connector, myDataStream) {
        @Override
        public byte[] getCode(int key, int modifiers) {
          if (key == KeyEvent.VK_ENTER && modifiers == 0 && myEnterKeyDefaultCodeEnabled) {
            // pty4j expects \r as Enter key code
            // https://github.com/JetBrains/pty4j/blob/0.9.4/test/com/pty4j/PtyTest.java#L54
            return LineSeparator.CR.getSeparatorBytes();
          }
          return super.getCode(key, modifiers);
        }
      };
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
        return TerminalExecutionConsole.this;
      }
      return super.getData(dataId);
    }
  }

  private class ClearAction extends DumbAwareAction {
    private ClearAction() {
      super(ExecutionBundle.messagePointer("clear.all.from.console.action.name"),
            ExecutionBundle.messagePointer("clear.all.from.console.action.text"), AllIcons.Actions.GC);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      clear();
    }
  }

  private class ScrollToTheEndAction extends DumbAwareAction {
    private ScrollToTheEndAction() {
      super(ActionsBundle.messagePointer("action.EditorConsoleScrollToTheEnd.text"),
            ActionsBundle.messagePointer("action.EditorConsoleScrollToTheEnd.text"),
            AllIcons.RunConfigurations.Scroll_down);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      BoundedRangeModel model = getBoundedRangeModel();
      e.getPresentation().setEnabled(model != null && model.getValue() != 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      BoundedRangeModel model = getBoundedRangeModel();
      if (model != null) {
        model.setValue(0);
      }
    }

    @Nullable
    private BoundedRangeModel getBoundedRangeModel() {
      return myTerminalWidget != null ? myTerminalWidget.getTerminalPanel().getBoundedRangeModel() : null;
    }
  }
}
