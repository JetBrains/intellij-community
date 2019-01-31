/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.terminal;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.DisposableWrapper;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.RegionPainter;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

public class JBTerminalWidget extends JediTermWidget implements Disposable {
  private final Project myProject;
  private final JBTerminalSystemSettingsProviderBase mySettingsProvider;
  private JBTerminalWidgetListener myListener;

  private JBTerminalWidgetDisposableWrapper myDisposableWrapper;
  private VirtualFile myVirtualFile;
  private String myCommandHistoryFilePath;

  public JBTerminalWidget(Project project,
                          JBTerminalSystemSettingsProviderBase settingsProvider,
                          Disposable parent) {
    this(project, 80, 24, settingsProvider, parent);
  }

  public JBTerminalWidget(Project project,
                          int columns,
                          int lines,
                          JBTerminalSystemSettingsProviderBase settingsProvider,
                          Disposable parent) {
    super(columns, lines, settingsProvider);
    myProject = project;
    mySettingsProvider = settingsProvider;

    setName("terminal");

    for (ConsoleFilterProvider eachProvider : ConsoleFilterProvider.FILTER_PROVIDERS.getExtensions()) {
      for (Filter filter : eachProvider.getDefaultFilters(project)) {
        addMessageFilter(project, filter);
      }
    }

    myDisposableWrapper = new JBTerminalWidgetDisposableWrapper(this, parent);
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
    bar.putClientProperty(JBScrollBar.TRACK, new RegionPainter<Object>() {
      @Override
      public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
        SubstringFinder.FindResult result = myTerminalPanel.getFindResult();
        if (result != null) {
          int modelHeight = bar.getModel().getMaximum() - bar.getModel().getMinimum();
          int anchorHeight = Math.max(2, height / modelHeight);

          Color color = mySettingsProvider.getTerminalColorPalette()
            .getColor(mySettingsProvider.getFoundPatternColor().getBackground());
          g.setColor(color);
          for (SubstringFinder.FindResult.FindItem r : result.getItems()) {
            int where = height * r.getStart().y / modelHeight;
            g.fillRect(x, y + where, width, anchorHeight);
          }
        }
      }
    });
    return bar;
  }

  @Override
  public List<TerminalAction> getActions() {
    List<TerminalAction> actions = super.getActions();
    actions.add(new TerminalAction("New Session", mySettingsProvider.getNewSessionKeyStrokes(), input -> {
      myListener.onNewSession();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
    actions.add(new TerminalAction("Select Previous Tab", mySettingsProvider.getPreviousTabKeyStrokes(), input -> {
      myListener.onPreviousTabSelected();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
    actions.add(new TerminalAction("Select Next Tab", mySettingsProvider.getNextTabKeyStrokes(), input -> {
      myListener.onNextTabSelected();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
    actions.add(new TerminalAction("Show Tabs", mySettingsProvider.getShowTabsKeyStrokes(), input -> {
      myListener.showTabs();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
    if (!mySettingsProvider.overrideIdeShortcuts()) {
      actions
        .add(new TerminalAction("EditorEscape", new KeyStroke[]{KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)}, input -> {
          if (!myTerminalPanel.getTerminalTextBuffer().isUsingAlternateBuffer()) {
            ToolWindowManager.getInstance(myProject).activateEditorComponent();
            return true;
          }
          else {
            return false;
          }
        }).withHidden(true));
    }
    actions.add(new TerminalAction("Close Session", mySettingsProvider.getCloseSessionKeyStrokes(), input -> {
      myListener.onSessionClosed();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T).withEnabledSupplier(() -> myListener != null));
    return actions;
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

  public void addMessageFilter(Project project, Filter filter) {
    addHyperlinkFilter(new HyperlinkFilter() {
      @Override
      public LinkResult apply(String line) {
        Filter.Result r = filter.applyFilter(line, line.length());
        if (r != null) {
          return new LinkResult(ContainerUtil.map(r.getResultItems(),
                                                  (item -> new LinkResultItem(item.getHighlightStartOffset(), item.getHighlightEndOffset(),
                                                                              new LinkInfo(
                                                                                () -> item.getHyperlinkInfo().navigate(project))))));
        }
        else {
          return null;
        }
      }
    });
  }

  public void start(TtyConnector connector) {
    setTtyConnector(connector);
    start();
  }

  public JBTerminalSystemSettingsProviderBase getSettingsProvider() {
    return mySettingsProvider;
  }

  public void moveDisposable(@NotNull Disposable newParent) {
    myDisposableWrapper = (JBTerminalWidgetDisposableWrapper)myDisposableWrapper.moveTo(newParent);
  }

  public void setVirtualFile(@Nullable VirtualFile virtualFile) {
    if (myVirtualFile != null && virtualFile != null) {
      throw new IllegalStateException("assigning a second virtual file to a terminal widget");
    }
    myVirtualFile = virtualFile;
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public void notifyStarted() {
    if (myListener != null) {
      myListener.onTerminalStarted();
    }
  }

  @Nullable
  public String getCommandHistoryFilePath() {
    return myCommandHistoryFilePath;
  }

  public void setCommandHistoryFilePath(@Nullable String commandHistoryFilePath) {
    myCommandHistoryFilePath = commandHistoryFilePath;
  }

  private static final class JBTerminalWidgetDisposableWrapper extends DisposableWrapper<JBTerminalWidget> {
    private final JBTerminalWidget myObject;

    private JBTerminalWidgetDisposableWrapper(JBTerminalWidget object, Disposable parent) {
      super(object, parent);
      myObject = object;
    }

    @Override
    public void dispose() {
      VirtualFile virtualFile = myObject.getVirtualFile();
      if (virtualFile != null && virtualFile.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) != null) {
        return; // don't dispose terminal widget during file reopening
      }
      super.dispose();
    }

    @NotNull
    @Override
    protected JBTerminalWidgetDisposableWrapper createNewWrapper(JBTerminalWidget object, @NotNull Disposable parent) {
      return new JBTerminalWidgetDisposableWrapper(object, parent);
    }
  }
}
