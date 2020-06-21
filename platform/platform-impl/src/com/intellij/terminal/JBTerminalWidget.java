// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.RegionPainter;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

public class JBTerminalWidget extends JediTermWidget implements Disposable, DataProvider {
  public static final DataKey<String> SELECTED_TEXT_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName() + " selected text");
  public static final DataKey<JBTerminalWidget> TERMINAL_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName());
  private static final Logger LOG = Logger.getInstance(JBTerminalWidget.class);

  private final JBTerminalSystemSettingsProviderBase mySettingsProvider;
  private final CompositeFilterWrapper myCompositeFilterWrapper;
  private JBTerminalWidgetListener myListener;

  private final @NotNull JBTerminalWidgetDisposableWrapper myDisposableWrapper;
  private @Nullable VirtualFile myVirtualFile;

  public JBTerminalWidget(@NotNull Project project,
                          @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                          @NotNull Disposable parent) {
    this(project, 80, 24, settingsProvider, null, parent);
  }

  public JBTerminalWidget(@NotNull Project project,
                          int columns,
                          int lines,
                          @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                          @Nullable TerminalExecutionConsole console,
                          @NotNull Disposable parent) {
    super(columns, lines, settingsProvider);
    mySettingsProvider = settingsProvider;
    myCompositeFilterWrapper = new CompositeFilterWrapper(project, console);
    addHyperlinkFilter(line -> runFilters(project, line));
    setName("terminal");
    myDisposableWrapper = new JBTerminalWidgetDisposableWrapper(parent);
  }

  @Nullable
  private LinkResult runFilters(@NotNull Project project, @NotNull String line) {
    Filter.Result r = ReadAction.compute(() -> {
      try {
        return myCompositeFilterWrapper.getCompositeFilter().applyFilter(line, line.length());
      }
      catch (ProcessCanceledException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipping running filters on " + line, e);
        }
        return null;
      }
    });
    if (r != null) {
      return new LinkResult(ContainerUtil.mapNotNull(r.getResultItems(), item -> convertResultItem(project, item)));
    }
    return null;
  }

  @Nullable
  private static LinkResultItem convertResultItem(@NotNull Project project, @NotNull Filter.ResultItem item) {
    HyperlinkInfo info = item.getHyperlinkInfo();
    if (info != null) {
      return new LinkResultItem(item.getHighlightStartOffset(), item.getHighlightEndOffset(),
                                new LinkInfo(() -> info.navigate(project)));
    }
    return null;
  }

  public JBTerminalWidgetListener getListener() {
    return myListener;
  }

  public void setListener(JBTerminalWidgetListener listener) {
    myListener = listener;
  }

  @Override
  protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                @NotNull StyleState styleState,
                                                @NotNull TerminalTextBuffer textBuffer) {
    JBTerminalPanel panel = new JBTerminalPanel((JBTerminalSystemSettingsProviderBase)settingsProvider, textBuffer, styleState);

    Disposer.register(this, panel);
    return panel;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
    return new JBTerminalStarter(terminal, connector);
  }

  @Override
  protected JScrollBar createScrollBar() {
    JBScrollBar bar = new JBScrollBar();
    bar.putClientProperty(JBScrollPane.Alignment.class, JBScrollPane.Alignment.RIGHT);
    bar.putClientProperty(JBScrollBar.TRACK, (RegionPainter<Object>)(g, x, y, width, height, object) -> {
      SubstringFinder.FindResult result = myTerminalPanel.getFindResult();
      if (result != null) {
        int modelHeight = bar.getModel().getMaximum() - bar.getModel().getMinimum();

        TerminalColor backgroundColor = mySettingsProvider.getFoundPatternColor().getBackground();
        if (backgroundColor != null) {
          g.setColor(mySettingsProvider.getTerminalColorPalette().getColor(backgroundColor));
        }
        int anchorHeight = Math.max(2, height / modelHeight);
        for (SubstringFinder.FindResult.FindItem r : result.getItems()) {
          int where = height * r.getStart().y / modelHeight;
          g.fillRect(x, y + where, width, anchorHeight);
        }
      }
    });
    return bar;
  }

  @Override
  public List<TerminalAction> getActions() {
    List<TerminalAction> actions = super.getActions();
    if (isInTerminalToolWindow()) {
      actions.add(new TerminalAction(mySettingsProvider.getNewSessionActionPresentation(), input -> {
        myListener.onNewSession();
        return true;
      }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
      actions.add(new TerminalAction(mySettingsProvider.getCloseSessionActionPresentation(), input -> {
        myListener.onSessionClosed();
        return true;
      }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));

      actions.add(TerminalSplitAction.create(true, myListener).withMnemonicKey(KeyEvent.VK_V).separatorBefore(true));
      actions.add(TerminalSplitAction.create(false, myListener).withMnemonicKey(KeyEvent.VK_H));
      if (myListener != null && myListener.isGotoNextSplitTerminalAvailable()) {
        actions.add(mySettingsProvider.getGotoNextSplitTerminalAction(myListener, true));
        actions.add(mySettingsProvider.getGotoNextSplitTerminalAction(myListener, false));
      }
      actions.add(new TerminalAction(mySettingsProvider.getPreviousTabActionPresentation(), input -> {
        myListener.onPreviousTabSelected();
        return true;
      }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
      actions.add(new TerminalAction(mySettingsProvider.getNextTabActionPresentation(), input -> {
        myListener.onNextTabSelected();
        return true;
      }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
      actions.add(new TerminalAction(mySettingsProvider.getMoveTabRightActionPresentation(), input -> {
        myListener.moveTabRight();
        return true;
      }).withMnemonicKey(KeyEvent.VK_R).withEnabledSupplier(() -> myListener != null && myListener.canMoveTabRight()));
      actions.add(new TerminalAction(mySettingsProvider.getMoveTabLeftActionPresentation(), input -> {
        myListener.moveTabLeft();
        return true;
      }).withMnemonicKey(KeyEvent.VK_L).withEnabledSupplier(() -> myListener != null && myListener.canMoveTabLeft()));
      actions.add(new TerminalAction(mySettingsProvider.getShowTabsActionPresentation(), input -> {
        myListener.showTabs();
        return true;
      }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
    }
    return actions;
  }

  private boolean isInTerminalToolWindow() {
    return isInTerminalToolWindow(DataManager.getInstance().getDataContext(myTerminalPanel));
  }

  static boolean isInTerminalToolWindow(@NotNull DataContext dataContext) {
    ToolWindow toolWindow = dataContext.getData(PlatformDataKeys.TOOL_WINDOW);
    return toolWindow != null && "Terminal".equals(toolWindow.getId());
  }

  @Override
  public void dispose() {
    close();
  }

  @Override
  protected SearchComponent createSearchComponent() {
    return new SearchComponent() {
      private final SearchTextField myTextField = new SearchTextField(false);

      @Override
      public String getText() {
        return myTextField.getText();
      }

      @Override
      public boolean ignoreCase() {
        return false;
      }

      @Override
      public JComponent getComponent() {
        myTextField.setOpaque(false);
        return myTextField;
      }

      @Override
      public void addDocumentChangeListener(DocumentListener listener) {
        myTextField.addDocumentListener(listener);
      }

      @Override
      public void addKeyListener(KeyListener listener) {
        myTextField.addKeyboardListener(listener);
      }

      @Override
      public void addIgnoreCaseListener(ItemListener listener) {

      }

      @Override
      public void onResultUpdated(SubstringFinder.FindResult result) {
      }

      @Override
      public void nextFindResultItem(SubstringFinder.FindResult.FindItem item) {
      }

      @Override
      public void prevFindResultItem(SubstringFinder.FindResult.FindItem item) {
      }
    };
  }

  public void addMessageFilter(@NotNull Filter filter) {
    myCompositeFilterWrapper.addFilter(filter);
  }

  public void start(TtyConnector connector) {
    setTtyConnector(connector);
    start();
  }

  public JBTerminalSystemSettingsProviderBase getSettingsProvider() {
    return mySettingsProvider;
  }

  public void moveDisposable(@NotNull Disposable newParent) {
    Disposer.register(newParent, myDisposableWrapper);
  }

  public void setVirtualFile(@Nullable VirtualFile virtualFile) {
    if (myVirtualFile != null && virtualFile != null) {
      throw new IllegalStateException("assigning a second virtual file to a terminal widget");
    }
    myVirtualFile = virtualFile;
  }

  public void notifyStarted() {
    if (myListener != null) {
      myListener.onTerminalStarted();
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (SELECTED_TEXT_DATA_KEY.is(dataId)) {
      return getSelectedText();
    }
    if (TERMINAL_DATA_KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  @Nullable
  private String getSelectedText() {
    TerminalPanel terminalPanel = getTerminalPanel();
    TerminalSelection selection = terminalPanel.getSelection();
    if (selection != null) {
      Pair<Point, Point> points = selection.pointsForRun(terminalPanel.getColumnCount());
      if (points.first != null && points.second != null) {
        TerminalTextBuffer buffer = terminalPanel.getTerminalTextBuffer();
        buffer.lock();
        try {
          return SelectionUtil.getSelectionText(points.first, points.second, buffer);
        }
        finally {
          buffer.unlock();
        }
      }
    }
    return null;
  }

  private final class JBTerminalWidgetDisposableWrapper implements Disposable {
    private JBTerminalWidgetDisposableWrapper(@NotNull Disposable parent) {
      Disposer.register(parent, this);
    }

    @Override
    public void dispose() {
      VirtualFile virtualFile = myVirtualFile;
      if (virtualFile != null && virtualFile.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != null) {
        return; // don't dispose terminal widget during file reopening
      }
      Disposer.dispose(JBTerminalWidget.this);
    }
  }
}
