// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ContentChooser<Data> extends DialogWrapper {
  public static final String RETURN_SYMBOL = "\u23ce";

  private List<Data> myAllContents;
  private Editor myViewer;

  private final boolean myUseIdeaEditor;

  private final JBList<Item> myList;
  private final JBSplitter mySplitter;
  private final Project myProject;
  private final boolean myAllowMultipleSelections;
  private final Alarm myUpdateAlarm;
  private Icon myListEntryIcon = AllIcons.FileTypes.Text;
  private boolean myUseNumbering = true;

  public ContentChooser(Project project, @NlsContexts.DialogTitle String title, boolean useIdeaEditor) {
    this(project, title, useIdeaEditor, false);
  }

  public ContentChooser(Project project, @NlsContexts.DialogTitle String title, boolean useIdeaEditor, boolean allowMultipleSelections) {
    super(project, true);
    myProject = project;
    myUseIdeaEditor = useIdeaEditor;
    myAllowMultipleSelections = allowMultipleSelections;
    myUpdateAlarm = new Alarm(getDisposable());
    mySplitter = new JBSplitter(true, 0.3f);
    mySplitter.setSplitterProportionKey(getDimensionServiceKey() + ".splitter");
    myList = new JBList<>(new CollectionListModel<>()) {
      @Override
      protected void doCopyToClipboardAction() {
        String text = getSelectedText();
        if (!text.isEmpty()) {
          CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }
      }
    };

    setOKButtonText(CommonBundle.getOkButtonText());
    setTitle(title);

    init();
  }

  public void setContentIcon(@Nullable Icon icon) {
    myListEntryIcon = icon;
  }

  public void setSplitterOrientation(boolean vertical) {
    mySplitter.setOrientation(vertical);
  }

  public void setUseNumbering(boolean useNumbering) {
    myUseNumbering = useNumbering;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    final int selectionMode = myAllowMultipleSelections ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                                                        : ListSelectionModel.SINGLE_SELECTION;
    myList.setSelectionMode(selectionMode);
    if (myUseIdeaEditor) {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      myList.setFont(scheme.getFont(EditorFontType.PLAIN));
      Color fg = ObjectUtils.chooseNotNull(scheme.getDefaultForeground(), JBColor.lazy(UIUtil::getListForeground));
      Color bg = ObjectUtils.chooseNotNull(scheme.getDefaultBackground(), JBColor.lazy(UIUtil::getListBackground));
      myList.setForeground(fg);
      myList.setBackground(bg);
    }

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        close(OK_EXIT_CODE);
        return true;
      }
    }.installOn(myList);


    MyListCellRenderer renderer = new MyListCellRenderer();
    myList.setCellRenderer(renderer);
    myList.addKeyListener(new KeyListener() {
      boolean doConsume;

      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int newSelectionIndex = -1;
          for (Item o : myList.getSelectedValuesList()) {
            int i = o.index;
            removeContentAt(myAllContents.get(i));
            if (newSelectionIndex < 0) {
              newSelectionIndex = i;
            }
          }

          rebuildListContent();
          if (myAllContents.isEmpty()) {
            close(CANCEL_EXIT_CODE);
            return;
          }
          newSelectionIndex = Math.min(newSelectionIndex, myAllContents.size() - 1);
          myList.setSelectedIndex(newSelectionIndex);
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          doOKAction();
        }
        else if (myUseNumbering) {
          SpeedSearchSupply supply = SpeedSearchSupply.getSupply(myList);
          if (supply != null && supply.isPopupActive()) return;
          char aChar = e.getKeyChar();
          if (aChar >= '0' && aChar <= '9') {
            int idx = aChar == '0' ? 9 : aChar - '1';
            if (idx < myAllContents.size()) {
              myList.setSelectedIndex(idx);
              e.consume();
              doConsume = true;
              // postpone doOKAction in order to handle all other (typed/released) key events
              // otherwise this events get to editor
              ApplicationManager.getApplication().invokeLater(() -> doOKAction());
            }
          }
        }
      }

      @Override
      public void keyTyped(KeyEvent e) {
        // we handle keyPressed for numbers and close dialog but we have to handle typed and released events too
        if (doConsume) {
          e.consume();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        // we handle keyPressed for numbers and close dialog but we have to handle typed and released events too
        if (doConsume) {
          e.consume();
        }
      }
    });

    mySplitter.setFirstComponent(ListWithFilter.wrap(
      myList, ScrollPaneFactory.createScrollPane(myList), o -> o.getShortText(renderer.previewChars), true));
    mySplitter.setSecondComponent(new JPanel());
    mySplitter.getFirstComponent().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        FontMetrics metrics = myList.getFontMetrics(myList.getFont());
        int charWidth = metrics.charWidth('i');
        renderer.previewChars = myList.getParent().getParent().getWidth() / charWidth + 10;
      }
    });
    rebuildListContent();

    ScrollingUtil.installActions(myList);
    ScrollingUtil.ensureSelectionExists(myList);
    updateViewerForSelection();
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myUpdateAlarm.isDisposed()) return;
        myUpdateAlarm.cancelAllRequests();
        myUpdateAlarm.addRequest(() -> updateViewerForSelection(), 100);
      }
    });

    mySplitter.setPreferredSize(JBUI.size(500, 500));

    SplitterProportionsData d = new SplitterProportionsDataImpl();
    d.externalizeToDimensionService(getClass().getName());
    d.restoreSplitterProportions(mySplitter);

    return mySplitter;
  }

  protected abstract void removeContentAt(final Data content);

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName(); // store different values for multi-paste, history and commit messages
  }

  @Override
  protected void doOKAction() {
    if (getSelectedIndex() < 0) return;
    super.doOKAction();
  }

  private void updateViewerForSelection() {
    if (myAllContents.isEmpty()) return;
    @NonNls String fullString = getSelectedText();

    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
    }

    if (myUseIdeaEditor) {
      myViewer = createIdeaEditor(fullString);
      JComponent component = myViewer.getComponent();
      component.setPreferredSize(JBUI.size(300, 500));
      mySplitter.setSecondComponent(component);
    }
    else {
      JTextArea textArea = new JTextArea(fullString);
      textArea.setRows(3);
      textArea.setWrapStyleWord(true);
      textArea.setLineWrap(true);
      textArea.setSelectionStart(0);
      textArea.setSelectionEnd(textArea.getText().length());
      textArea.setEditable(false);
      mySplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(textArea));
    }
    mySplitter.revalidate();
  }

  protected Editor createIdeaEditor(String text) {
    Document doc = EditorFactory.getInstance().createDocument(text);
    Editor editor = EditorFactory.getInstance().createViewer(doc, myProject);
    editor.getSettings().setFoldingOutlineShown(false);
    editor.getSettings().setLineNumbersShown(false);
    editor.getSettings().setLineMarkerAreaShown(false);
    editor.getSettings().setIndentGuidesShown(false);
    return editor;
  }

  @Override
  public void dispose() {
    super.dispose();

    SplitterProportionsData d = new SplitterProportionsDataImpl();
    d.externalizeToDimensionService(getClass().getName());
    d.saveSplitterProportions(mySplitter);

    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
      myViewer = null;
    }
  }

  private void rebuildListContent() {
    List<Item> items = new ArrayList<>();
    int index = 0;
    List<Data> contents = new ArrayList<>(getContents());
    for (Data content : contents) {
      String longText = getStringRepresentationFor(content);
      if (!StringUtil.isEmpty(longText)) {
        items.add(new Item(index, longText));
      }
      index++;
    }
    myAllContents = contents;
    FilteringListModel<Item> listModel = (FilteringListModel<Item>)myList.getModel();
    ((CollectionListModel<?>)listModel.getOriginalModel()).removeAll();
    listModel.addAll(items);
    ListWithFilter<?> listWithFilter = ComponentUtil.getParentOfType(ListWithFilter.class, myList);
    if (listWithFilter != null) {
      listWithFilter.getSpeedSearch().update();
      if (listModel.getSize() == 0) listWithFilter.resetFilter();
    }
  }

  @Nullable
  protected abstract @NlsSafe String getStringRepresentationFor(Data content);

  @NotNull
  protected abstract List<Data> getContents();

  public int getSelectedIndex() {
    Item o = myList.getSelectedValue();
    return o == null? -1 : o.index;
  }

  public void setSelectedIndex(int index) {
    myList.setSelectedIndex(index);
    ScrollingUtil.ensureIndexIsVisible(myList, index, 0);
    updateViewerForSelection();
  }

  @NotNull
  public List<Data> getSelectedContents() {
    return JBIterable.from(myList.getSelectedValuesList()).map(o -> myAllContents.get(o.index)).toList();
  }

  @NotNull
  public List<Data> getAllContents() {
    return myAllContents;
  }

  @NotNull
  public String getSelectedText() {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Item o : myList.getSelectedValuesList()) {
      if (first) first = false;
      else sb.append("\n");
      String s = o.longText;
      sb.append(StringUtil.convertLineSeparators(s));
    }
    return sb.toString();
  }

  private class MyListCellRenderer extends ColoredListCellRenderer<Item> {
    int previewChars = 80;

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Item value, int index, boolean selected, boolean hasFocus) {
      setIcon(myListEntryIcon);
      if (myUseIdeaEditor && myUseNumbering) {
        int max = list.getModel().getSize();
        String indexString = String.valueOf(index + 1);
        int count = String.valueOf(max).length() - indexString.length();
        String prefix = indexString + StringUtil.repeatSymbol(' ', count) + "  ";
        append(prefix, SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
      }

      String text = value.getShortText(previewChars);
      append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected);
    }
  }

  public static class Item {
    final int index;
    protected final String longText;
    String shortText = "";
    boolean trimmed;

    protected Item(int index, String longText) {
      this.index = index;
      this.longText = longText;
    }

    public @NlsSafe String getShortText(int maxChars) {
      int len = shortText.length();
      if (len > 0 && !trimmed) return shortText;
      if (len >= maxChars && (len - maxChars) * 10 / len == 0) return shortText;
      if (len > maxChars) {
        shortText = StringUtil.first(shortText, maxChars, true);
        trimmed = true;
        return shortText;
      }
      boolean hasSlashR = StringUtil.indexOf(longText, '\r', 0, Math.min(longText.length(), maxChars * 2 + 1)) > 0;
      if (!hasSlashR) {
        String s = StringUtil.first(longText, maxChars, true);
        trimmed = !Strings.areSameInstance(s, longText);
        shortText = StringUtil.convertLineSeparators(s, RETURN_SYMBOL);
      }
      else {
        String s = StringUtil.first(longText, maxChars * 2 + 1, false);
        String s2 = StringUtil.convertLineSeparators(s, RETURN_SYMBOL);
        shortText = StringUtil.first(s2, maxChars, true);
        trimmed = !Strings.areSameInstance(s, longText) || !Strings.areSameInstance(s2, shortText);
      }
      return shortText;
    }
  }
}
