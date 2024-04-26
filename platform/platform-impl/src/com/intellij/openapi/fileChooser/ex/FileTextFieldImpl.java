// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.fileChooser.ex.FileLookup.Finder;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFilter;
import com.intellij.openapi.fileChooser.ex.FileTextFieldUtil.CompletionResult;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListActions;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ui.update.LazyUiDisposableKt;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_CODE_COMPLETION;
import static com.intellij.openapi.application.ModalityState.stateForComponent;

public class FileTextFieldImpl implements FileTextField, Disposable {
  private final JTextField myPathTextField;

  private CompletionResult myCurrentCompletion;

  private JBPopup myCurrentPopup;
  private JBPopup myNoSuggestionsPopup;

  private JList<LookupFile> myList;

  private MergingUpdateQueue myUiUpdater;

  private boolean myPathIsUpdating;
  private Finder myFinder;
  private LookupFilter myFilter;

  protected boolean myAutopopup = false;
  private FileTextFieldImpl.CancelAction myCancelAction;
  private final Set<Action> myDisabledTextActions;

  private volatile Map<String, String> myMacroMap;

  public FileTextFieldImpl(JTextField field, Finder finder, LookupFilter filter, Map<String, String> macroMap, Disposable parent) {
    myPathTextField = field;
    myMacroMap = new TreeMap<>(macroMap);

    InputMap listMap = (InputMap)UIManager.getDefaults().get("List.focusInputMap");
    KeyStroke[] listKeys = listMap.keys();
    myDisabledTextActions = new HashSet<>();
    for (KeyStroke eachListStroke : listKeys) {
      String listActionID = (String)listMap.get(eachListStroke);
      if (ListActions.Down.ID.equals(listActionID) || ListActions.Up.ID.equals(listActionID)) {
        Object textActionID = field.getInputMap().get(eachListStroke);
        if (textActionID != null) {
          Action textAction = field.getActionMap().get(textActionID);
          if (textAction != null) {
            myDisabledTextActions.add(textAction);
          }
        }
      }
    }

    FileTextFieldImpl assigned = (FileTextFieldImpl)myPathTextField.getClientProperty(KEY);
    if (assigned != null) {
      assigned.myFinder = finder;
      assigned.myFilter = filter;
      return;
    }
    myPathTextField.putClientProperty(KEY, this);

    boolean headless = ApplicationManager.getApplication().isUnitTestMode();
    myUiUpdater = new MergingUpdateQueue("FileTextField.UiUpdater", 200, false, myPathTextField);
    if (!headless) {
      UiNotifyConnector.installOn(myPathTextField, myUiUpdater);
    }

    myFinder = finder;
    myFilter = filter;

    myPathTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        processTextChanged();
      }
    });

    myPathTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        processListSelection(e);
      }
    });

    myPathTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        closePopup();
      }
    });

    myCancelAction = new CancelAction();

    LazyUiDisposableKt.lazyUiDisposable(parent, field, this, (child, project) -> {
      Disposer.register(child, myUiUpdater);
      return Unit.INSTANCE;
    });
  }

  @SuppressWarnings("unused") //used by rider
  public void resetMacroMap(Map<String, String> macroMap) {
    myMacroMap = new TreeMap<>(macroMap);
  }

  @Override
  public void dispose() {
    myUiUpdater = null;
  }

  private void processTextChanged() {
    if (myAutopopup && !isPathUpdating()) {
      // hide current popup ASAP
      hideCurrentPopup();
      suggestCompletion(false, false);
    }

    onTextChanged(getTextFieldText());
  }

  protected void onTextChanged(String newValue) { }

  private void suggestCompletion(boolean selectReplacedText, boolean isExplicitCall) {
    if (isExplicitCall) {
      myAutopopup = true;
    }

    if (!getField().isFocusOwner()) return;

    LookupFile preselected = null;
    if (myList != null && myCurrentCompletion != null) {
      int index = myList.getSelectedIndex();
      if (index >= 0 && index < myList.getModel().getSize()) {
        preselected = myList.getSelectedValue();
      }
    }

    LookupFile _preselected = preselected;
    myUiUpdater.queue(new Update("textField.suggestCompletion") {
      @Override
      public void run() {
        String completionBase = getCompletionBase();
        if (completionBase == null) return;

        LookupFile file = myFinder.find(completionBase);
        if (file != null && file.exists() && !file.isDirectory()) {
          return;  // we've entered a complete path already, no need to autopopup completion again (IDEA-78996)
        }

        boolean showAdText = completionBase.length() != myPathTextField.getText().length();

        EmptyProgressIndicator indicator = new EmptyProgressIndicator(stateForComponent(myPathTextField));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          CompletionResult result = ProgressManager.getInstance().runProcess(
            () -> FileTextFieldUtil.processCompletion(completionBase, myFinder, myFilter, myMacroMap, _preselected),
            indicator);

          SwingUtilities.invokeLater(() -> {
            if (completionBase.equals(getCompletionBase())) {
              selectCompletionRemoveText(result, selectReplacedText);
              showCompletionPopup(result, isExplicitCall, showAdText);
            }
          });
        });
      }
    });
  }

  private void selectCompletionRemoveText(CompletionResult result, boolean selectReplacedText) {
    int pos = myPathTextField.getCaretPosition();
    if (!result.variants.isEmpty() && selectReplacedText) {
      myPathTextField.setCaretPosition(myPathTextField.getText().length());
      myPathTextField.moveCaretPosition(pos);
    }
  }

  private void showCompletionPopup(CompletionResult result, boolean isExplicit, boolean showAdText) {
    if (myList == null) {
      myList = new JBList<>();
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myList.setCellRenderer(new GroupedItemsListRenderer<>(new ListItemDescriptorAdapter<>() {
        @Override
        public String getTextFor(LookupFile file) {
          return FileTextFieldUtil.getLookupString(file, myFinder, myCurrentCompletion);
        }

        @Override
        public Icon getIconFor(LookupFile value) {
          return value.getIcon();
        }

        private @NlsContexts.Separator @Nullable String getSeparatorAboveOf(LookupFile file) {
          if (myCurrentCompletion == null) return null;

          int fileIndex = myCurrentCompletion.variants.indexOf(file);
          if (fileIndex > 0 && !myCurrentCompletion.macros.contains(file)) {
            LookupFile prev = myCurrentCompletion.variants.get(fileIndex - 1);
            if (myCurrentCompletion.macros.contains(prev)) {
              return "";
            }
          }

          if (myCurrentCompletion.kidsAfterSeparator.indexOf(file) == 0 && !myCurrentCompletion.siblings.isEmpty()) {
            LookupFile parent = file.getParent();
            return parent != null ? parent.getName() : "";
          }

          if (!myCurrentCompletion.macros.isEmpty() && fileIndex == 0) {
            return getPathVariablesSeparatorText();
          }

          return null;
        }

        @Override
        public boolean hasSeparatorAboveOf(LookupFile value) {
          return getSeparatorAboveOf(value) != null;
        }

        @Override
        public String getCaptionAboveOf(LookupFile value) {
          return getSeparatorAboveOf(value);
        }
      }));
    }

    if (myCurrentPopup != null) {
      closePopup();
    }

    myCurrentCompletion = result;

    if (result.variants.isEmpty()) {
      showNoSuggestions(isExplicit);
      return;
    }

    myList.setModel(new AbstractListModel<>() {
      @Override
      public int getSize() {
        return myCurrentCompletion.variants.size();
      }

      @Override
      public LookupFile getElementAt(int index) {
        return myCurrentCompletion.variants.get(index);
      }
    });
    myList.getSelectionModel().clearSelection();

    String adText = null;
    if (showAdText) {
      String strokeText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("EditorChooseLookupItemReplace"));
      adText = IdeBundle.message("file.chooser.completion.ad.text", strokeText);
    }

    //noinspection DuplicatedCode
    myCurrentPopup = new PopupChooserBuilder<>(myList)
      .addListener(new JBPopupListener() {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          myPathTextField.registerKeyboardAction(myCancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
          for (Action each : myDisabledTextActions) {
            each.setEnabled(false);
          }
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          myPathTextField.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
          for (Action each : myDisabledTextActions) {
            each.setEnabled(true);
          }
        }
      })
      .setRequestFocus(false)
      .setAdText(adText)
      .setAutoSelectIfEmpty(false)
      .setResizable(false)
      .setCancelCallback(() -> {
        int caret = myPathTextField.getCaretPosition();
        myPathTextField.setSelectionStart(caret);
        myPathTextField.setSelectionEnd(caret);
        myPathTextField.setFocusTraversalKeysEnabled(true);
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getField(), true));
        return Boolean.TRUE;
      })
      .setItemChosenCallback(() -> processChosenFromCompletion(false))
      .setCancelKeyEnabled(false)
      .setAlpha(0.1f)
      .setFocusOwners(new Component[]{myPathTextField})
      .createPopup();

    if (result.preselected != null) {
      myList.setSelectedValue(result.preselected, false);
    }

    myPathTextField.setFocusTraversalKeysEnabled(false);

    myCurrentPopup.showInScreenCoordinates(getField(), getLocationForCaret(myPathTextField));
  }

  private void processChosenFromCompletion(boolean nameOnly) {
    FileTextFieldUtil.processChosenFromCompletion(
      getSelectedFileFromCompletionPopup(),
      new FileTextFieldUtil.TextFieldDocumentOwner(myPathTextField, this::setTextToFile),
      myFinder,
      nameOnly
    );
  }

  protected @NlsContexts.Separator @NotNull String getPathVariablesSeparatorText() {
    return IdeBundle.message("file.chooser.completion.path.variables.text");
  }

  private void showNoSuggestions(boolean isExplicit) {
    hideCurrentPopup();

    if (!isExplicit) return;

    JComponent message = HintUtil.createErrorLabel(IdeBundle.message("file.chooser.completion.no.suggestions"));
    myNoSuggestionsPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(message, message)
      .setRequestFocus(false).setResizable(false).setAlpha(0.1f).setFocusOwners(new Component[]{myPathTextField})
      .createPopup();
    myNoSuggestionsPopup.showInScreenCoordinates(getField(), getLocationForCaret(myPathTextField));
  }

  private void hideCurrentPopup() {
    if (myCurrentPopup != null) {
      myCurrentPopup.cancel();
      myCurrentPopup = null;
    }
    if (myNoSuggestionsPopup != null) {
      myNoSuggestionsPopup.cancel();
      myNoSuggestionsPopup = null;
    }
  }

  private static Point getLocationForCaret(JTextComponent pathTextField) {
    Point point;

    int position = pathTextField.getCaretPosition();
    try {
      Rectangle2D rec = pathTextField.modelToView2D(position);
      point = new Point((int)rec.getMaxX(), (int)rec.getMaxY());
    }
    catch (BadLocationException e) {
      point = pathTextField.getCaret().getMagicCaretPosition();
    }

    SwingUtilities.convertPointToScreen(point, pathTextField);

    point.y += 2;

    return point;
  }

  public @Nullable LookupFile getFile() {
    String text = getTextFieldText();
    return text != null ? myFinder.find(text) : null;
  }

  protected void setTextToFile(LookupFile file) {
    String text = file.getAbsolutePath();
    if (file.isDirectory() && !text.endsWith(myFinder.getSeparator())) {
      text += myFinder.getSeparator();
    }
    myPathTextField.setText(text);
  }

  private void processListSelection(KeyEvent e) {
    if (togglePopup(e)) return;

    if (!isPopupShowing()) return;

    InputMap map = myPathTextField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    if (map != null) {
      Object object = map.get(KeyStroke.getKeyStrokeForEvent(e));
      if (object instanceof Action action) {
        if (action.isEnabled()) {
          action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "action"));
          e.consume();
          return;
        }
      }
    }

    Object action = getAction(e, myList);

    if (ListActions.Down.ID.equals(action)) {
      if (ensureSelectionExists()) {
        ScrollingUtil.moveDown(myList, e.getModifiersEx());
        e.consume();
      }
    }
    else if (ListActions.Up.ID.equals(action)) {
      ScrollingUtil.moveUp(myList, e.getModifiersEx());
      e.consume();
    }
    else if (ListActions.PageDown.ID.equals(action)) {
      ScrollingUtil.movePageDown(myList);
      e.consume();
    }
    else if (ListActions.PageUp.ID.equals(action)) {
      ScrollingUtil.movePageUp(myList);
      e.consume();
    }
    else if (getSelectedFileFromCompletionPopup() != null &&
             (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB) &&
             e.getModifiersEx() == 0) {
      hideCurrentPopup();
      e.consume();
      processChosenFromCompletion(e.getKeyCode() == KeyEvent.VK_TAB);
    }
  }

  private @Nullable LookupFile getSelectedFileFromCompletionPopup() {
    return myList != null ? myList.getSelectedValue() : null;
  }

  private boolean ensureSelectionExists() {
    if (myList.getSelectedIndex() < 0 || myList.getSelectedIndex() >= myList.getModel().getSize()) {
      if (myList.getModel().getSize() >= 0) {
        myList.setSelectedIndex(0);
        return false;
      }
    }

    return true;
  }

  private boolean togglePopup(KeyEvent e) {
    @SuppressWarnings("deprecation") KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    Object action = ((InputMap)UIManager.get("ComboBox.ancestorInputMap")).get(stroke);
    if ("selectNext".equals(action)) {
      if (!isPopupShowing()) {
        return true;
      }
      else {
        return false;
      }
    }
    else if ("togglePopup".equals(action)) {
      if (isPopupShowing()) {
        closePopup();
      }
      else {
        suggestCompletion(true, true);
      }
      return true;
    }
    else {
      KeymapManager manager = KeymapManager.getInstance();
      if (manager != null) {
        Keymap active = manager.getActiveKeymap();
        for (String id : active.getActionIds(stroke)) {
          if (ACTION_CODE_COMPLETION.equals(id)) {
            suggestCompletion(true, true);
          }
        }
      }
    }

    return false;
  }

  private static Object getAction(KeyEvent e, JComponent comp) {
    @SuppressWarnings("deprecation") KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    return comp.getInputMap().get(stroke);
  }

  @Override
  public JTextField getField() {
    return myPathTextField;
  }

  @Override
  public boolean isPopupDisplayed() {
    return myCurrentPopup != null && myCurrentPopup.isVisible();
  }

  public Finder getFinder() {
    return myFinder;
  }

  private boolean isPopupShowing() {
    return myCurrentPopup != null && myList != null && myList.isShowing();
  }

  private void closePopup() {
    hideCurrentPopup();
    myCurrentCompletion = null;
  }

  public @Nullable String getTextFieldText() {
    return myPathTextField.getText();
  }

  public final void setText(String text, boolean now, @Nullable Runnable onDone) {
    Update update = new Update("pathFromTree") {
      @Override
      public void run() {
        myPathIsUpdating = true;
        getField().setText(text);
        myPathIsUpdating = false;
        if (onDone != null) {
          onDone.run();
        }
      }
    };
    if (now) {
      update.run();
    }
    else {
      myUiUpdater.queue(update);
    }
  }

  public boolean isPathUpdating() {
    return myPathIsUpdating;
  }

  public @Nullable String getCompletionBase() {
    String text = getTextFieldText();
    if (text == null) return null;
    int pos = myPathTextField.getCaretPosition();
    return pos < text.length() ? text.substring(0, pos) : text;
  }

  private final class CancelAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myCurrentPopup != null) {
        myAutopopup = false;
        hideCurrentPopup();
      }
    }
  }
}
