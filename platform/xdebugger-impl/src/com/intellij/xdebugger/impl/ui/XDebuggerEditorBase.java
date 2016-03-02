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
package com.intellij.xdebugger.impl.ui;

import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ClickListener;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XDebuggerEditorBase {
  private final Project myProject;
  private final XDebuggerEditorsProvider myDebuggerEditorsProvider;
  @NotNull private final EvaluationMode myMode;
  @Nullable private final String myHistoryId;
  @Nullable private XSourcePosition mySourcePosition;
  private int myHistoryIndex = -1;

  private final JLabel myChooseFactory = new JLabel();
  private WeakReference<ListPopup> myPopup;

  protected XDebuggerEditorBase(final Project project,
                                @NotNull XDebuggerEditorsProvider debuggerEditorsProvider,
                                @NotNull EvaluationMode mode,
                                @Nullable @NonNls String historyId,
                                final @Nullable XSourcePosition sourcePosition) {
    myProject = project;
    myDebuggerEditorsProvider = debuggerEditorsProvider;
    myMode = mode;
    myHistoryId = historyId;
    mySourcePosition = sourcePosition;

    myChooseFactory.setToolTipText(XDebuggerBundle.message("xdebugger.evaluate.language.hint"));
    myChooseFactory.setBorder(JBUI.Borders.empty(0, 3, 0, 3));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (myChooseFactory.isEnabled()) {
          ListPopup oldPopup = SoftReference.dereference(myPopup);
          if (oldPopup != null && !oldPopup.isDisposed()) {
            oldPopup.cancel();
            myPopup = null;
            return true;
          }
          ListPopup popup = createLanguagePopup();
          popup.showUnderneathOf(myChooseFactory);
          myPopup = new WeakReference<ListPopup>(popup);
          return true;
        }
        return false;
      }
    }.installOn(myChooseFactory);
  }

  private ListPopup createLanguagePopup() {
    DefaultActionGroup actions = new DefaultActionGroup();
    for (final Language language : getEditorsProvider().getSupportedLanguages(myProject, mySourcePosition)) {
      //noinspection ConstantConditions
      actions.add(new AnAction(language.getDisplayName(), null, language.getAssociatedFileType().getIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          XExpression currentExpression = getExpression();
          setExpression(new XExpressionImpl(currentExpression.getExpression(), language, currentExpression.getCustomInfo()));
          requestFocusInEditor();
        }
      });
    }

    DataContext dataContext = DataManager.getInstance().getDataContext(getComponent());
    return JBPopupFactory.getInstance().createActionGroupPopup("Choose Language", actions, dataContext,
                                                               JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                               false);
  }

  protected JPanel addChooseFactoryLabel(JComponent component, boolean top) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);

    JPanel factoryPanel = new JPanel(new BorderLayout());
    factoryPanel.add(myChooseFactory, top ? BorderLayout.NORTH : BorderLayout.CENTER);
    panel.add(factoryPanel, BorderLayout.WEST);
    return panel;
  }

  public void setSourcePosition(@Nullable XSourcePosition sourcePosition) {
    if (mySourcePosition != sourcePosition) {
      mySourcePosition = sourcePosition;
      setExpression(getExpression());
    }
  }

  @NotNull
  public EvaluationMode getMode() {
    return myMode;
  }

  @Nullable
  public abstract Editor getEditor();

  public abstract JComponent getComponent();

  public JComponent getEditorComponent() {
    return getComponent();
  }

  protected abstract void doSetText(XExpression text);

  public void setExpression(@Nullable XExpression text) {
    if (text == null) {
      text = getMode() == EvaluationMode.EXPRESSION ? XExpressionImpl.EMPTY_EXPRESSION : XExpressionImpl.EMPTY_CODE_FRAGMENT;
    }
    Language language = text.getLanguage();
    if (language == null) {
      if (mySourcePosition != null) {
        language = LanguageUtil.getFileLanguage(mySourcePosition.getFile());
      }
      if (language == null) {
        language = LanguageUtil.getFileTypeLanguage(getEditorsProvider().getFileType());
      }
    }
    text = new XExpressionImpl(text.getExpression(), language, text.getCustomInfo(), getMode());

    Collection<Language> languages = getEditorsProvider().getSupportedLanguages(myProject, mySourcePosition);
    boolean many = languages.size() > 1;

    if (language != null) {
      myChooseFactory.setVisible(many);
    }
    myChooseFactory.setVisible(myChooseFactory.isVisible() || many);
    //myChooseFactory.setEnabled(many && languages.contains(language));

    if (language != null && language.getAssociatedFileType() != null) {
      Icon icon = language.getAssociatedFileType().getIcon();
      myChooseFactory.setIcon(icon);
      myChooseFactory.setDisabledIcon(IconLoader.getDisabledIcon(icon));
    }

    doSetText(text);
  }

  public abstract XExpression getExpression();

  @Nullable
  public abstract JComponent getPreferredFocusedComponent();

  public void requestFocusInEditor() {
    JComponent preferredFocusedComponent = getPreferredFocusedComponent();
    if (preferredFocusedComponent != null) {
      IdeFocusManager.getInstance(myProject).requestFocus(preferredFocusedComponent, true);
    }
  }

  public abstract void selectAll();

  protected void onHistoryChanged() {
  }

  public List<XExpression> getRecentExpressions() {
    if (myHistoryId != null) {
      return XDebuggerHistoryManager.getInstance(myProject).getRecentExpressions(myHistoryId);
    }
    return Collections.emptyList();
  }

  public void saveTextInHistory() {
    saveTextInHistory(getExpression());
  }

  private void saveTextInHistory(final XExpression text) {
    if (myHistoryId != null) {
      boolean update = XDebuggerHistoryManager.getInstance(myProject).addRecentExpression(myHistoryId, text);
      myHistoryIndex = -1; //meaning not from the history list
      if (update) {
        onHistoryChanged();
      }
    }
  }

  @NotNull
  protected FileType getFileType(@NotNull XExpression expression) {
    FileType fileType = LanguageUtil.getLanguageFileType(expression.getLanguage());
    if (fileType != null) {
      return fileType;
    }
    return getEditorsProvider().getFileType();
  }

  public XDebuggerEditorsProvider getEditorsProvider() {
    return myDebuggerEditorsProvider;
  }

  public Project getProject() {
    return myProject;
  }

  protected Document createDocument(final XExpression text) {
    return getEditorsProvider().createDocument(getProject(), text, mySourcePosition, myMode);
  }

  public boolean canGoBackward() {
    return myHistoryIndex < getRecentExpressions().size()-1;
  }

  public boolean canGoForward() {
    return myHistoryIndex > 0;
  }

  public void goBackward() {
    final List<XExpression> expressions = getRecentExpressions();
    if (myHistoryIndex < expressions.size() - 1) {
      myHistoryIndex++;
      setExpression(expressions.get(myHistoryIndex));
    }
  }

  public void goForward() {
    final List<XExpression> expressions = getRecentExpressions();
    if (myHistoryIndex > 0) {
      myHistoryIndex--;
      setExpression(expressions.get(myHistoryIndex));
    }
  }
}
