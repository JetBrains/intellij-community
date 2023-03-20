// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.reference.SoftReference;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class XDebuggerEditorBase implements Expandable {
  private final Project myProject;
  private final XDebuggerEditorsProvider myDebuggerEditorsProvider;
  @NotNull private final EvaluationMode myMode;
  @Nullable private final String myHistoryId;
  @Nullable private XSourcePosition mySourcePosition;
  private int myHistoryIndex = -1;
  @Nullable private PsiElement myContext;

  private final LanguageChooser myLanguageChooser = new LanguageChooser();
  private final JLabel myExpandButton = new JLabel(AllIcons.General.ExpandComponent);
  private JBPopup myExpandedPopup;

  private Runnable myExpandHandler;

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

    // setup expand button
    myExpandButton.setToolTipText(KeymapUtil.createTooltipText(IdeBundle.message("action.expand"), "ExpandExpandableComponent"));
    myExpandButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myExpandButton.setBorder(JBUI.Borders.empty(0, 3));
    myExpandButton.setDisabledIcon(AllIcons.General.ExpandComponent);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        expand();
        return true;
      }
    }.installOn(myExpandButton);
    myExpandButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myExpandButton.setIcon(AllIcons.General.ExpandComponentHover);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myExpandButton.setIcon(AllIcons.General.ExpandComponent);
      }
    });
  }

  @NotNull
  private Collection<Language> getSupportedLanguages() {
    XDebuggerEditorsProvider editorsProvider = getEditorsProvider();
    if (myContext != null && editorsProvider instanceof XDebuggerEditorsProviderBase) {
      return ((XDebuggerEditorsProviderBase)editorsProvider).getSupportedLanguages(myContext);
    }
    else {
      return editorsProvider.getSupportedLanguages(myProject, mySourcePosition);
    }
  }

  protected JComponent decorate(JComponent component, boolean multiline, boolean showEditor) {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel();

    JPanel factoryPanel = JBUI.Panels.simplePanel();
    factoryPanel.add(myLanguageChooser, multiline ? BorderLayout.NORTH : BorderLayout.CENTER);
    panel.add(factoryPanel, BorderLayout.WEST);

    if (!multiline && showEditor) {
      component = addExpand(component, false);
    }

    panel.addToCenter(component);

    if (multiline) {
      JBLabel adLabel = new JBLabel(getAdText(), SwingConstants.RIGHT);
      adLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
      adLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
      panel.addToBottom(adLabel);
    }

    return panel;
  }

  protected JComponent addChooser(JComponent component) {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(component);
    panel.setBackground(JBColor.lazy(() -> component.getBackground()));
    panel.addToRight(myLanguageChooser);
    return panel;
  }

  protected JComponent addExpand(JComponent component, boolean inheritBackground) {
    BorderLayoutPanel panel;
    if (inheritBackground) {
      panel = new BorderLayoutPanel() {
        @Override
        public Color getBackground() {
          return component.getBackground();
        }
      };
    }
    else {
      panel = JBUI.Panels.simplePanel();
      panel.setOpaque(false);
    }
    panel.addToCenter(component);
    panel.addToRight(myExpandButton);
    return panel;
  }

  public JComponent getLanguageChooser() {
    return myLanguageChooser;
  }

  public void setContext(@Nullable PsiElement context) {
    if (myContext != context) {
      myContext = context;
      setExpression(getExpression());
    }
  }

  public void setSourcePosition(@Nullable XSourcePosition sourcePosition) {
    if (mySourcePosition != sourcePosition) {
      mySourcePosition = sourcePosition;
      XExpression expression = getExpression();
      // for empty expression we reset the language from the source position
      if (XDebuggerUtilImpl.isEmptyExpression(expression) && expression.getLanguage() != null) {
        expression = XExpressionImpl.changeLanguage(expression, null);
      }
      setExpression(expression);
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
      if (myContext != null) {
        language = myContext.getLanguage();
      }
      if (language == null && mySourcePosition != null) {
        language = LanguageUtil.getFileLanguage(mySourcePosition.getFile());
      }
      if (language == null) {
        language = LanguageUtil.getFileTypeLanguage(getEditorsProvider().getFileType());
      }
      text = new XExpressionImpl(text.getExpression(), language, text.getCustomInfo(), text.getMode());
    }

    myLanguageChooser.requestUpdate(language);

    doSetText(text);
  }

  public void setEnabled(boolean enable) {
    myLanguageChooser.setEnabled(enable);
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

  public final Project getProject() {
    return myProject;
  }

  protected Document createDocument(final XExpression text) {
    if (myProject.isDefault()) {
      return new DocumentImpl(text.getExpression());
    }
    XDebuggerEditorsProvider provider = getEditorsProvider();
    if (myContext != null && provider instanceof XDebuggerEditorsProviderBase) {
      return ((XDebuggerEditorsProviderBase)provider).createDocument(myProject, text, myContext, myMode);
    }
    else {
      return provider.createDocument(myProject, text, mySourcePosition, myMode);
    }
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

  protected static void foldNewLines(EditorEx editor) {
    editor.getColorsScheme().setAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES, null);
    editor.reinitSettings();
    FoldingModelEx foldingModel = editor.getFoldingModel();
    CharSequence text = editor.getDocument().getCharsSequence();
    foldingModel.runBatchFoldingOperation(() -> {
      foldingModel.clearFoldRegions();
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
          foldingModel.createFoldRegion(i, i + 1, "\u23ce", null, true);
        }
      }
    });
  }

  protected void prepareEditor(EditorEx editor) {
  }

  protected final void setExpandable(Editor editor) {
    editor.getContentComponent().putClientProperty(Expandable.class, this);
  }

  @Override
  public void expand() {
    if (myExpandedPopup != null || !getComponent().isEnabled()) return;

    if (myExpandHandler != null) {
      myExpandHandler.run();
      return;
    }

    XDebuggerExpressionEditor expressionEditor =
      new XDebuggerExpressionEditor(myProject, myDebuggerEditorsProvider, myHistoryId, mySourcePosition,
                                    getExpression(), true, true, false) {
        @Override
        protected JComponent decorate(JComponent component, boolean multiline, boolean showEditor) {
          return component;
        }
      };

    EditorTextField editorTextField = (EditorTextField)expressionEditor.getEditorComponent();
    editorTextField.addSettingsProvider(this::prepareEditor);
    editorTextField.addSettingsProvider(this::setExpandable);
    editorTextField.setFont(editorTextField.getFont().deriveFont((float)getEditor().getColorsScheme().getEditorFontSize()));

    JComponent component = expressionEditor.getComponent();
    component.setPreferredSize(new Dimension(getComponent().getWidth(), 100));

    myExpandedPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(component, expressionEditor.getPreferredFocusedComponent())
      .setMayBeParent(true)
      .setFocusable(true)
      .setResizable(true)
      .setRequestFocus(true)
      .setLocateByContent(true)
      .setCancelOnWindowDeactivation(false)
      .setAdText(getAdText())
      .setKeyboardActions(Collections.singletonList(Pair.create(event -> {
        collapse();
        Window window = ComponentUtil.getWindow(getComponent());
        if (window != null) {
          window.dispatchEvent(
            new KeyEvent(getComponent(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_MASK, KeyEvent.VK_ENTER, '\r'));
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK))))
      .setCancelCallback(() -> {
        setExpression(expressionEditor.getExpression());
        requestFocusInEditor();
        Editor baseEditor = getEditor();
        if (baseEditor != null) {
          foldNewLines((EditorEx)baseEditor);
          Editor newEditor = expressionEditor.getEditor();
          if (newEditor != null) {
            copyCaretPosition(newEditor, baseEditor);
            PropertiesComponent.getInstance().setValue(SOFT_WRAPS_KEY, newEditor.getSoftWrapModel().isSoftWrappingEnabled());
          }
        }
        myExpandedPopup = null;
        return true;
      }).createPopup();

    myExpandedPopup.show(new RelativePoint(getComponent(), new Point(0, 0)));

    EditorEx editor = (EditorEx)expressionEditor.getEditor();
    copyCaretPosition(getEditor(), editor);
    editor.getSettings().setUseSoftWraps(isUseSoftWraps());

    addCollapseButton(editor, this::collapse);

    expressionEditor.requestFocusInEditor();
  }

  public void setExpandHandler(Runnable handler) {
    myExpandHandler = handler;
  }

  public void addCollapseButton(Runnable handler) {
    JComponent component = getEditorComponent();
    if (component instanceof EditorTextField) {
      ((EditorTextField)component).addSettingsProvider(editor -> {
        editor.getContentComponent().putClientProperty(Expandable.class, new Expandable() {
          @Override
          public void expand() {
          }

          @Override
          public void collapse() {
            handler.run();
          }

          @Override
          public boolean isExpanded() {
            return true;
          }
        });
        addCollapseButton(editor, handler);
      });
    }
  }

  private static void addCollapseButton(EditorEx editor, Runnable handler) {
    ErrorStripeEditorCustomization.DISABLED.customize(editor);
    // TODO: copied from ExpandableTextField
    JScrollPane pane = editor.getScrollPane();
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    pane.getVerticalScrollBar().add(JBScrollBar.LEADING, new JLabel(AllIcons.General.CollapseComponent) {{
      setToolTipText(KeymapUtil.createTooltipText(IdeBundle.message("action.collapse"), "CollapseExpandableComponent"));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setBorder(JBUI.Borders.empty(5, 0, 5, 5));
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent event) {
          setIcon(AllIcons.General.CollapseComponentHover);
        }

        @Override
        public void mouseExited(MouseEvent event) {
          setIcon(AllIcons.General.CollapseComponent);
        }

        @Override
        public void mouseClicked(MouseEvent event) {
          handler.run();
        }
      });
    }});
  }

  @NotNull
  private static @NlsContexts.Label String getAdText() {
    return XDebuggerBundle.message("xdebugger.evaluate.history.navigate.ad",
                                   NlsMessages.formatAndList(Arrays.asList(
                                     KeymapUtil.getKeystrokeText(KeymapUtil.getKeyStroke(CommonShortcuts.MOVE_DOWN)),
                                     KeymapUtil.getKeystrokeText(KeymapUtil.getKeyStroke(CommonShortcuts.MOVE_UP)))));
  }

  public static void copyCaretPosition(@Nullable Editor source, @Nullable Editor destination) {
    if (source != null && destination != null) {
      destination.getCaretModel().moveToOffset(source.getCaretModel().getOffset());
    }
  }

  @Override
  public void collapse() {
    if (myExpandedPopup != null) {
      myExpandedPopup.cancel();
    }
  }

  @Override
  public boolean isExpanded() {
    return myExpandedPopup != null;
  }

  private class LanguageChooser extends JLabel {
    @SuppressWarnings("UseJBColor")
    final Color ENABLED_COLOR = new Color(0x787878);
    final Color DISABLED_COLOR = new JBColor(0xB2B2B2, 0x5C5D5F);

    private Collection<Language> myLanguages = Collections.emptyList();
    private WeakReference<ListPopup> myPopup;

    LanguageChooser() {
      setHorizontalTextPosition(SwingConstants.LEFT);
      setIconTextGap(0);
      setToolTipText(XDebuggerBundle.message("xdebugger.evaluate.language.hint"));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setBorder(JBUI.Borders.emptyRight(2));
      Icon dropdownIcon = AllIcons.General.Dropdown;
      int width = dropdownIcon.getIconWidth();
      dropdownIcon = IconUtil.cropIcon(dropdownIcon, new Rectangle(width / 2, 0, width - width / 2, dropdownIcon.getIconHeight()));
      LayeredIcon icon = JBUIScale.scaleIcon(new LayeredIcon(1));
      icon.setIcon(dropdownIcon, 0, 0, -5);
      setIcon(icon);
      setDisabledIcon(IconLoader.getDisabledIcon(icon));

      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (isEnabled()) {
            ListPopup oldPopup = SoftReference.dereference(myPopup);
            if (oldPopup != null && !oldPopup.isDisposed()) {
              oldPopup.cancel();
              myPopup = null;
              return true;
            }
            ListPopup popup = createLanguagePopup();
            popup.showUnderneathOf(LanguageChooser.this);
            myPopup = new WeakReference<>(popup);
            return true;
          }
          return false;
        }
      }.installOn(this);
    }

    @Override
    public Color getForeground() {
      return isEnabled() ? ENABLED_COLOR : DISABLED_COLOR;
    }

    ListPopup createLanguagePopup() {
      DefaultActionGroup actions = new DefaultActionGroup();
      for (Language language : myLanguages) {
        //noinspection ConstantConditions
        actions.add(new AnAction(language.getDisplayName(), null, language.getAssociatedFileType().getIcon()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            setExpression(XExpressionImpl.changeLanguage(getExpression(), language));
            requestFocusInEditor();
          }
        });
      }

      DataContext dataContext = DataManager.getInstance().getDataContext(XDebuggerEditorBase.this.getComponent());
      return JBPopupFactory.getInstance()
        .createActionGroupPopup(XDebuggerBundle.message("debugger.editor.choose.language"), actions, dataContext,
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                false);
    }

    void requestUpdate(Language currentLanguage) {
      ReadAction.nonBlocking(() -> getSupportedLanguages())
        .inSmartMode(myProject)
        .finishOnUiThread(ModalityState.any(), languages -> {
          boolean many = languages.size() > 1;
          myLanguages = languages;

          if (currentLanguage != null) {
            setVisible(many);
          }
          setVisible(isVisible() || many);

          if (currentLanguage != null && currentLanguage.getAssociatedFileType() != null) {
            setText(currentLanguage.getDisplayName());
          }
        })
        .coalesceBy(this)
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  private static final String SOFT_WRAPS_KEY = "XDebuggerExpressionEditor_Use_Soft_Wraps";

  public boolean isUseSoftWraps() {
    return PropertiesComponent.getInstance().getBoolean(SOFT_WRAPS_KEY, true);
  }

  public void setUseSoftWraps(boolean use) {
    PropertiesComponent.getInstance().setValue(SOFT_WRAPS_KEY, use);
    Editor editor = getEditor();
    if (editor != null) {
      AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editor, null, use);
    }
  }
}
