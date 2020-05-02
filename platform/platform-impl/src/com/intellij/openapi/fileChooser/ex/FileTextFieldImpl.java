// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ListActions;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ui.update.LazyUiDisposable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_CODE_COMPLETION;
import static com.intellij.openapi.application.ModalityState.stateForComponent;

public abstract class FileTextFieldImpl implements FileLookup, Disposable, FileTextField {

  private static final Logger LOG = Logger.getInstance(FileTextFieldImpl.class);

  private final Object myLock = new Object();
  private final JTextField myPathTextField;

  private CompletionResult myCurrentCompletion;

  private JBPopup myCurrentPopup;
  private JBPopup myNoSuggestionsPopup;

  private JList myList;

  private MergingUpdateQueue myUiUpdater;

  private boolean myPathIsUpdating;
  private Finder myFinder;
  private LookupFilter myFilter;
  private String myCompletionBase;

  private int myCurrentCompletionsPos = 1;
  private String myFileSpitRegExp;

  protected boolean myAutopopup = false;
  private FileTextFieldImpl.CancelAction myCancelAction;
  private final Set<Action> myDisabledTextActions;
  private Map<String, String> myMacroMap;

  public FileTextFieldImpl(Finder finder, LookupFilter filter, Map<String, String> macroMap) {
    this(new JTextField(), finder, filter, macroMap, null);
  }

  public FileTextFieldImpl(final JTextField field, Finder finder, LookupFilter filter, Map<String, String> macroMap, final Disposable parent) {
    myPathTextField = field;
    myMacroMap = new TreeMap<>(macroMap);

    final InputMap listMap = (InputMap)UIManager.getDefaults().get("List.focusInputMap");
    final KeyStroke[] listKeys = listMap.keys();
    myDisabledTextActions = new HashSet<>();
    for (KeyStroke eachListStroke : listKeys) {
      final String listActionID = (String)listMap.get(eachListStroke);
      if (ListActions.Down.ID.equals(listActionID) || ListActions.Up.ID.equals(listActionID)) {
        final Object textActionID = field.getInputMap().get(eachListStroke);
        if (textActionID != null) {
          final Action textAction = field.getActionMap().get(textActionID);
          if (textAction != null) {
            myDisabledTextActions.add(textAction);
          }
        }
      }
    }


    final FileTextFieldImpl assigned = (FileTextFieldImpl)myPathTextField.getClientProperty(KEY);
    if (assigned != null) {
      assigned.myFinder = finder;
      assigned.myFilter = filter;
      return;
    }

    myPathTextField.putClientProperty(KEY, this);
    final boolean headless = ApplicationManager.getApplication().isUnitTestMode();

    myUiUpdater = new MergingUpdateQueue("FileTextField.UiUpdater", 200, false, myPathTextField);
    if (!headless) {
      new UiNotifyConnector(myPathTextField, myUiUpdater);
    }

    myFinder = finder;
    myFilter = filter;

    myFileSpitRegExp = myFinder.getSeparator().replaceAll("\\\\", "\\\\\\\\");

    myPathTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(final DocumentEvent e) {
        processTextChanged();
      }

      @Override
      public void removeUpdate(final DocumentEvent e) {
        processTextChanged();
      }

      @Override
      public void changedUpdate(final DocumentEvent e) {
        processTextChanged();
      }
    });

    myPathTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(final KeyEvent e) {
        processListSelection(e);
      }
    });

    myPathTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(final FocusEvent e) {
        closePopup();
      }
    });

    myCancelAction = new CancelAction();


    new LazyUiDisposable<FileTextFieldImpl>(parent, field, this) {
      @Override
      protected void initialize(@NotNull Disposable parent, @NotNull FileTextFieldImpl child, @Nullable Project project) {
        Disposer.register(child, myUiUpdater);
      }
    };
  }

  public void resetMacroMap(Map<String, String> macroMap) {
    synchronized (myLock) {
      myMacroMap = new TreeMap<>(macroMap);
    }
  }

  @Override
  public void dispose() {
    myUiUpdater = null;
  }

  private void processTextChanged() {
    if (myAutopopup && !isPathUpdating()) {
      // Hide current popup as early as we can
      hideCurrentPopup();
      suggestCompletion(false, false);
    }

    onTextChanged(getTextFieldText());
  }

  protected void onTextChanged(final String newValue) {
  }

  private void suggestCompletion(final boolean selectReplacedText, final boolean isExplicitCall) {
    if (isExplicitCall) {
      myAutopopup = true;
    }

    if (!getField().isFocusOwner()) return;

    final CompletionResult result = new CompletionResult();
    if (myList != null && myCurrentCompletion != null) {
      int index = myList.getSelectedIndex();
      if (index >= 0 && index < myList.getModel().getSize()) {
        result.myPreselected = (LookupFile)myList.getSelectedValue();
      }
    }

    myUiUpdater.queue(new Update("textField.suggestCompletion") {
      @Override
      public void run() {
        final String completionBase = getCompletionBase();
        if (completionBase != null) {
          final LookupFile file = myFinder.find(completionBase);
          if (file != null && file.exists() && !file.isDirectory()) {
            // we've entered a complete path already, no need to autopopup completion again (IDEA-78996)
            return;
          }
        }
        result.myCompletionBase = completionBase;
        if (result.myCompletionBase == null) return;
        result.myFieldText = myPathTextField.getText();
        EmptyProgressIndicator indicator = new EmptyProgressIndicator(stateForComponent(myPathTextField));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          ProgressManager.getInstance().runProcess(() -> processCompletion(result), indicator);
          SwingUtilities.invokeLater(() -> {
            if (!result.myCompletionBase.equals(getCompletionBase())) return;

            int pos = selectCompletionRemoveText(result, selectReplacedText);

            showCompletionPopup(result, pos, isExplicitCall);
          });
        });
      }
    });

  }

  private int selectCompletionRemoveText(final CompletionResult result, boolean selectReplacedText) {
    int pos = myPathTextField.getCaretPosition();

    if (result.myToComplete.size() > 0 && selectReplacedText) {
      myPathTextField.setCaretPosition(myPathTextField.getText().length());
      myPathTextField.moveCaretPosition(pos);
    }

    return pos;
  }

  @Nullable
  public String getAdText(CompletionResult result) {
    if (result.myCompletionBase == null) return null;
    if (result.myCompletionBase.length() == result.myFieldText.length()) return null;

    String strokeText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(
            "EditorChooseLookupItemReplace"));
    return IdeBundle.message("file.chooser.completion.ad.text", strokeText);
  }

  public static class CompletionResult {
    public List<LookupFile> myMacros;
    public List<LookupFile> myToComplete;
    public List<LookupFile> mySiblings;
    public List<LookupFile> myKidsAfterSeparator;
    public String myCompletionBase;
    public LookupFile myClosestParent;
    public LookupFile myPreselected;
    public LookupFile current;
    public boolean currentParentMatch;
    public String effectivePrefix;
    public LookupFile currentGrandparent;
    public String grandparentPrefix;
    public boolean closedPath;
    public String myFieldText;
  }

  private static class Separator {
    private final String myText;

    private Separator(final String text) {
      myText = text;
    }

    public String getText() {
      return myText;
    }
  }

  private void showCompletionPopup(final CompletionResult result, int position, boolean isExplicit) {
    if (myList == null) {
      myList = new JBList();
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myList.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptorAdapter() {
        @Override
        public String getTextFor(final Object value) {
          final LookupFile file = (LookupFile)value;

          if (file.getMacro() != null) {
            return file.getMacro();
          } else {
            return (myCurrentCompletion != null && myCurrentCompletion.myKidsAfterSeparator.contains(file) ? myFinder.getSeparator() : "") +
                   file.getName();
          }

        }

        @Override
        public Icon getIconFor(final Object value) {
          final LookupFile file = (LookupFile)value;
          return file.getIcon();
        }

        @Nullable
        private Separator getSeparatorAboveOf(Object value) {
          if (myCurrentCompletion == null) return null;
          final LookupFile file = (LookupFile)value;

          final int fileIndex = myCurrentCompletion.myToComplete.indexOf(file);
          if (fileIndex > 0 && !myCurrentCompletion.myMacros.contains(file)) {
            final LookupFile prev = myCurrentCompletion.myToComplete.get(fileIndex - 1);
            if (myCurrentCompletion.myMacros.contains(prev)) {
              return new Separator("");
            }
          }


          if (myCurrentCompletion.myKidsAfterSeparator.indexOf(file) == 0 && myCurrentCompletion.mySiblings.size() > 0) {
            final LookupFile parent = file.getParent();
            return parent == null ? new Separator("") : new Separator(parent.getName());
          }

          if (myCurrentCompletion.myMacros.size() > 0 && fileIndex == 0) {
            return new Separator(getPathVariablesSeparatorText());
          }

          return null;
        }

        @Override
        public boolean hasSeparatorAboveOf(final Object value) {
          return getSeparatorAboveOf(value) != null;
        }

        @Override
        public String getCaptionAboveOf(final Object value) {
          final FileTextFieldImpl.Separator separator = getSeparatorAboveOf(value);
          return separator != null ? separator.getText() : null;
        }
      }));
    }


    if (myCurrentPopup != null) {
      closePopup();
    }

    myCurrentCompletion = result;
    myCurrentCompletionsPos = position;

    if (myCurrentCompletion.myToComplete.size() == 0) {
      showNoSuggestions(isExplicit);
      return;
    }

    myList.setModel(new AbstractListModel() {
      @Override
      public int getSize() {
        return myCurrentCompletion.myToComplete.size();
      }

      @Override
      public Object getElementAt(final int index) {
        return myCurrentCompletion.myToComplete.get(index);
      }
    });
    myList.getSelectionModel().clearSelection();
    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(myList);
    builder.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        myPathTextField
          .registerKeyboardAction(myCancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
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
    });

    myCurrentPopup =
      builder.setRequestFocus(false).setAdText(getAdText(myCurrentCompletion)).setAutoSelectIfEmpty(false).setResizable(false).setCancelCallback(
        () -> {
          final int caret = myPathTextField.getCaretPosition();
          myPathTextField.setSelectionStart(caret);
          myPathTextField.setSelectionEnd(caret);
          myPathTextField.setFocusTraversalKeysEnabled(true);
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getField(), true));
          return Boolean.TRUE;
        }).setItemChoosenCallback(() -> processChosenFromCompletion(false)).setCancelKeyEnabled(false).setAlpha(0.1f).setFocusOwners(new Component[]{myPathTextField}).
          createPopup();


    if (result.myPreselected != null) {
      myList.setSelectedValue(result.myPreselected, false);
    }

    myPathTextField.setFocusTraversalKeysEnabled(false);

    myCurrentPopup.showInScreenCoordinates(getField(), getLocationForCaret(myPathTextField));
  }

  @NotNull
  protected String getPathVariablesSeparatorText() {
    return IdeBundle.message("file.chooser.completion.path.variables.text");
  }

  private void showNoSuggestions(boolean isExplicit) {
    hideCurrentPopup();

    if (!isExplicit) return;

    final JComponent message = HintUtil.createErrorLabel(IdeBundle.message("file.chooser.completion.no.suggestions"));
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(message, message);
    builder.setRequestFocus(false).setResizable(false).setAlpha(0.1f).setFocusOwners(new Component[] {myPathTextField});
    myNoSuggestionsPopup = builder.createPopup();
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
      final Rectangle rec = pathTextField.modelToView(position);
      point = new Point((int)rec.getMaxX(), (int)rec.getMaxY());
    }
    catch (BadLocationException e) {
      point = pathTextField.getCaret().getMagicCaretPosition();
    }

    SwingUtilities.convertPointToScreen(point, pathTextField);

    point.y += 2;

    return point;
  }

  public void processCompletion(final CompletionResult result) {
    result.myToComplete = new ArrayList<>();
    result.mySiblings = new ArrayList<>();
    result.myKidsAfterSeparator = new ArrayList<>();
    final String typed = result.myCompletionBase;

    if (typed == null) return;

    addMacroPaths(result, typed);

    final String typedText = myFinder.normalize(typed);


    result.current = getClosestParent(typed);
    result.myClosestParent = result.current;

    if (result.current != null) {
      result.currentParentMatch = SystemInfo.isFileSystemCaseSensitive
                                         ? typedText.equals(result.current.getAbsolutePath())
                                         : typedText.equalsIgnoreCase(result.current.getAbsolutePath());

      result.closedPath = typed.endsWith(myFinder.getSeparator()) && typedText.length() > myFinder.getSeparator().length();
      final String currentParentText = result.current.getAbsolutePath();

      if (!StringUtil.toUpperCase(typedText).startsWith(StringUtil.toUpperCase(currentParentText))) return;

      String prefix = typedText.substring(currentParentText.length());
      if (prefix.startsWith(myFinder.getSeparator())) {
        prefix = prefix.substring(myFinder.getSeparator().length());
      }
      else if (typed.endsWith(myFinder.getSeparator())) {
        prefix = "";
      }

      result.effectivePrefix = prefix;

      result.currentGrandparent = result.current.getParent();
      if (result.currentGrandparent != null && result.currentParentMatch && !result.closedPath) {
        final String currentGrandparentText = result.currentGrandparent.getAbsolutePath();
        if (StringUtil.startsWithConcatenation(typedText, currentGrandparentText, myFinder.getSeparator())) {
          result.grandparentPrefix = currentParentText.substring(currentGrandparentText.length() + myFinder.getSeparator().length());
        }
      }
    } else {
      result.effectivePrefix = typedText;
    }


    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (result.current != null) {
          result.myToComplete.addAll(getMatchingChildren(result.effectivePrefix, result.current));

          if (result.currentParentMatch && !result.closedPath && !typed.isEmpty()) {
            result.myKidsAfterSeparator.addAll(result.myToComplete);
          }

          if (result.grandparentPrefix != null) {
            final List<LookupFile> siblings = getMatchingChildren(result.grandparentPrefix, result.currentGrandparent);
            result.myToComplete.addAll(0, siblings);
            result.mySiblings.addAll(siblings);
          }
        }

        int currentDiff = Integer.MIN_VALUE;
        LookupFile toPreselect = result.myPreselected;

        if (toPreselect == null || !result.myToComplete.contains(toPreselect)) {
          boolean toPreselectFixed = false;
          if (result.effectivePrefix.length() > 0) {
            for (LookupFile each : result.myToComplete) {
              String eachName = StringUtil.toUpperCase(each.getName());
              if (!eachName.startsWith(result.effectivePrefix)) continue;
              int diff = result.effectivePrefix.compareTo(eachName);
              currentDiff = Math.max(diff, currentDiff);
              if (currentDiff == diff) {
                toPreselect = each;
                toPreselectFixed = true;
                break;
              }
            }

            if (!toPreselectFixed) {
              toPreselect = null;
            }
          }
          else {
            toPreselect = null;
          }

          if (toPreselect == null) {
            if (result.myToComplete.size() == 1) {
              toPreselect = result.myToComplete.get(0);
            }
            else if (result.effectivePrefix.length() == 0) {
              if (result.mySiblings.size() > 0) {
                toPreselect = result.mySiblings.get(0);
              }
            }

            if (toPreselect == null && !result.myToComplete.contains(toPreselect) && result.myToComplete.size() > 0) {
              toPreselect = result.myToComplete.get(0);
            }
          }
        }

        if (result.currentParentMatch && result.mySiblings.size() > 0) {
          toPreselect = null;
        }

        result.myPreselected = toPreselect;
      }

      private List<LookupFile> getMatchingChildren(String prefix, LookupFile parent) {
        final MinusculeMatcher matcher = createMatcher(prefix);
        return parent.getChildren(new LookupFilter() {
          @Override
          public boolean isAccepted(final LookupFile file) {
            return !file.equals(result.current) && myFilter.isAccepted(file) && matcher.matches(file.getName());
          }
        });
      }
    });
  }

  private static MinusculeMatcher createMatcher(String prefix) {
    return NameUtil.buildMatcher("*" + prefix, NameUtil.MatchingCaseSensitivity.NONE);
  }

  private void addMacroPaths(final CompletionResult result, final String typedText) {
    result.myMacros = new ArrayList<>();

    MinusculeMatcher matcher = createMatcher(typedText);

    Map<String, String> macroMap;
    synchronized (myLock) {
      macroMap = myMacroMap;
    }

    for (String eachMacro : macroMap.keySet()) {
      if (matcher.matches(eachMacro)) {
        final String eachPath = macroMap.get(eachMacro);
        if (eachPath != null) {
          final LookupFile macroFile = myFinder.find(eachPath);
          if (macroFile != null && macroFile.exists()) {
            result.myMacros.add(macroFile);
            result.myToComplete.add(macroFile);
            macroFile.setMacro(eachMacro);
          }
        }
      }
    }
  }

  @Nullable
  private LookupFile getClosestParent(final String typed) {
    if (typed == null) return null;
    LookupFile lastFound = myFinder.find(typed);
    if (lastFound == null) return null;
    if (typed.isEmpty()) return lastFound;
    if (lastFound.exists()) {
      if (typed.charAt(typed.length() - 1) != File.separatorChar) return lastFound.getParent();
      return lastFound;
    }

    final String[] splits = myFinder.normalize(typed).split(myFileSpitRegExp);
    StringBuilder fullPath = new StringBuilder();
    for (int i = 0; i < splits.length; i++) {
      String each = splits[i];
      fullPath.append(each);
      if (i < splits.length - 1) {
        fullPath.append(myFinder.getSeparator());
      }
      final LookupFile file = myFinder.find(fullPath.toString());
      if (file == null || !file.exists()) return lastFound;
      lastFound = file;
    }

    return lastFound;
  }

  @Nullable
  public LookupFile getFile() {
    String text = getTextFieldText();
    if (text == null) return null;
    return myFinder.find(text);
  }

  private void processChosenFromCompletion(boolean nameOnly) {
    final LookupFile file = getSelectedFileFromCompletionPopup();
    if (file == null) return;

    if (nameOnly) {
      try {
        final Document doc = myPathTextField.getDocument();
        int caretPos = myPathTextField.getCaretPosition();
        if (myFinder.getSeparator().equals(doc.getText(caretPos, 1))) {
          for (;caretPos < doc.getLength(); caretPos++) {
            final String eachChar = doc.getText(caretPos, 1);
            if (!myFinder.getSeparator().equals(eachChar)) break;
          }
        }

        int start = caretPos > 0 ? caretPos - 1 : caretPos;
        while(start >= 0) {
          final String each = doc.getText(start, 1);
          if (myFinder.getSeparator().equals(each)) {
            start++;
            break;
          }
          start--;
        }

        int end = start < caretPos ? caretPos : start;
        while(end <= doc.getLength()) {
          final String each = doc.getText(end, 1);
          if (myFinder.getSeparator().equals(each)) {
            break;
          }
          end++;
        }

        if (end > doc.getLength()) {
          end = doc.getLength();
        }

        if (start > end || start < 0 || end > doc.getLength()) {
          setTextToFile(file);
        } else {
          replacePathComponent(file, caretPos, start, end);
        }
      }
      catch (BadLocationException e) {
        LOG.error(e);
      }
    } else {
      setTextToFile(file);
    }
  }

  /**
   * Replace the path component under the caret with the file selected from the completion list.
   *
   * @param file     the selected file.
   * @param caretPos
   * @param start    the start offset of the path component under the caret.
   * @param end      the end offset of the path component under the caret.
   * @throws BadLocationException
   */
  private void replacePathComponent(LookupFile file, int caretPos, int start, int end) throws BadLocationException {
    final Document doc = myPathTextField.getDocument();

    myPathTextField.setSelectionStart(0);
    myPathTextField.setSelectionEnd(0);

    final String name = file.getName();
    boolean toRemoveExistingName;
    String prefix = "";

    if (caretPos >= start) {
      prefix = doc.getText(start, caretPos - start);
      if (prefix.length() == 0) {
        prefix = doc.getText(start, end - start);
      }
      if (SystemInfo.isFileSystemCaseSensitive) {
        toRemoveExistingName = name.startsWith(prefix) && prefix.length() > 0;
      } else {
        toRemoveExistingName = StringUtil.toUpperCase(name).startsWith(StringUtil.toUpperCase(prefix)) && prefix.length() > 0;
      }
    } else {
      toRemoveExistingName = true;
    }

    int newPos;
    if (toRemoveExistingName) {
      doc.remove(start, end - start);
      doc.insertString(start, name, doc.getDefaultRootElement().getAttributes());
      newPos = start + name.length();
    } else {
      doc.insertString(caretPos, name, doc.getDefaultRootElement().getAttributes());
      newPos = caretPos + name.length();
    }

    if (file.isDirectory()) {
      if (!myFinder.getSeparator().equals(doc.getText(newPos, 1))) {
        doc.insertString(newPos, myFinder.getSeparator(), doc.getDefaultRootElement().getAttributes());
        newPos++;
      }
    }

    if (newPos < doc.getLength()) {
      if (myFinder.getSeparator().equals(doc.getText(newPos, 1))) {
        newPos++;
      }
    }
    myPathTextField.setCaretPosition(newPos);
  }

  protected void setTextToFile(final LookupFile file) {
    String text = file.getAbsolutePath();
    if (file.isDirectory() && !text.endsWith(myFinder.getSeparator())) {
      text += myFinder.getSeparator();
    }
    myPathTextField.setText(text);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void processListSelection(final KeyEvent e) {
    if (togglePopup(e)) return;

    if (!isPopupShowing()) return;

    final InputMap map = myPathTextField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    if (map != null) {
      final Object object = map.get(KeyStroke.getKeyStrokeForEvent(e));
      if (object instanceof Action) {
        final Action action = (Action)object;
        if (action.isEnabled()) {
          action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "action"));
          e.consume();
          return;
        }
      }
    }

    final Object action = getAction(e, myList);

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
    else if (getSelectedFileFromCompletionPopup() != null && (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB) && e.getModifiers() == 0) {
      hideCurrentPopup();
      e.consume();
      processChosenFromCompletion(e.getKeyCode() == KeyEvent.VK_TAB);
    }
  }

  private
  @Nullable
  LookupFile getSelectedFileFromCompletionPopup() {
    if (myList == null) return null;
    return (LookupFile)myList.getSelectedValue();
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

  @SuppressWarnings("HardCodedStringLiteral")
  private boolean togglePopup(KeyEvent e) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Object action = ((InputMap)UIManager.get("ComboBox.ancestorInputMap")).get(stroke);
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
      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      for (String id : active.getActionIds(stroke)) {
        if (ACTION_CODE_COMPLETION.equals(id)) {
          suggestCompletion(true, true);
        }
      }
    }

    return false;
  }

  private static Object getAction(final KeyEvent e, final JComponent comp) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
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

  @Nullable
  public String getTextFieldText() {
    final String text = myPathTextField.getText();
    if (text == null) return null;
    return text;
  }

  public final void setText(final String text, boolean now, @Nullable final Runnable onDone) {
    final Update update = new Update("pathFromTree") {
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

  public
  @Nullable
  String getCompletionBase() {
    String text = getTextFieldText();
    if (text == null) return null;
    int pos = myPathTextField.getCaretPosition();
    return pos < text.length() ? text.substring(0, pos) : text;
  }

  public static class Vfs extends FileTextFieldImpl {

    public Vfs(JTextField field,
               Map<String, String> macroMap,
               Disposable parent, final LookupFilter chooserFilter) {
      super(field, new LocalFsFinder(), chooserFilter, macroMap, parent);
    }

    public Vfs(Map<String, String> macroMap,
               Disposable parent, final LookupFilter chooserFilter) {
      this(new JTextField(), macroMap, parent, chooserFilter);
    }

    @Override
    public VirtualFile getSelectedFile() {
      LookupFile lookupFile = getFile();
      return lookupFile != null ? ((LocalFsFinder.VfsFile)lookupFile).getFile() : null;
    }
  }

  private class CancelAction implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      if (myCurrentPopup != null) {
        myAutopopup = false;
        hideCurrentPopup();
      }
    }
  }
}
