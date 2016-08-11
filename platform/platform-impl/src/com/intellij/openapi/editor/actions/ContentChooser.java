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
package com.intellij.openapi.editor.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ContentChooser<Data> extends DialogWrapper {
  private List<Data> myAllContents;
  private Editor     myViewer;

  private final boolean myUseIdeaEditor;

  private final JBList     myList;
  private final JBSplitter mySplitter;
  private final Project    myProject;
  private final boolean    myAllowMultipleSelections;
  private final Alarm      myUpdateAlarm;
  private Icon myListEntryIcon = AllIcons.FileTypes.Text;

  public ContentChooser(Project project, String title, boolean useIdeaEditor) {
    this(project, title, useIdeaEditor, false);
  }

  public ContentChooser(Project project, String title, boolean useIdeaEditor, boolean allowMultipleSelections) {
    super(project, true);
    myProject = project;
    myUseIdeaEditor = useIdeaEditor;
    myAllowMultipleSelections = allowMultipleSelections;
    myUpdateAlarm = new Alarm(getDisposable());
    mySplitter = new JBSplitter(true, 0.3f);
    mySplitter.setSplitterProportionKey(getDimensionServiceKey() + ".splitter");
    myList = new JBList(new CollectionListModel<Item>());
    myList.setExpandableItemsEnabled(false);

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
      Color fg = ObjectUtils.chooseNotNull(scheme.getDefaultForeground(), new JBColor(UIUtil::getListForeground));
      Color bg = ObjectUtils.chooseNotNull(scheme.getDefaultBackground(), new JBColor(UIUtil::getListBackground));
      myList.setForeground(fg);
      myList.setBackground(bg);
    }

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        close(OK_EXIT_CODE);
        return true;
      }
    }.installOn(myList);


    myList.setCellRenderer(new MyListCellRenderer());
    myList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int newSelectionIndex = -1;
          for (Object o : myList.getSelectedValues()) {
            int i = ((Item)o).index;
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
        else {
          final char aChar = e.getKeyChar();
          if (aChar >= '0' && aChar <= '9') {
            int idx = aChar == '0' ? 9 : aChar - '1';
            if (idx < myAllContents.size()) {
              myList.setSelectedIndex(idx);
              e.consume();
              doOKAction();
            }
          }
        }
      }
    });

    mySplitter.setFirstComponent(ListWithFilter.wrap(myList, ScrollPaneFactory.createScrollPane(myList), o -> ((Item)o).longText));
    mySplitter.setSecondComponent(new JPanel());
    rebuildListContent();

    ScrollingUtil.installActions(myList);
    ScrollingUtil.ensureSelectionExists(myList);
    updateViewerForSelection();
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
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
    String fullString = getSelectedText();

    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
    }

    if (myUseIdeaEditor) {
      myViewer = createIdeaEditor(fullString);
      JComponent component = myViewer.getComponent();
      component.setPreferredSize(JBUI.size(300, 500));
      mySplitter.setSecondComponent(component);
    } else {
      final JTextArea textArea = new JTextArea(fullString);
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
    ArrayList<Item> items = new ArrayList<>();
    int i = 0;
    List<Data> contents = new ArrayList<>(getContents());
    for (Data content : contents) {
      String fullString = getStringRepresentationFor(content);
      if (fullString != null) {
        String shortString;
        fullString = StringUtil.convertLineSeparators(fullString);
        int newLineIdx = fullString.indexOf('\n');
        if (newLineIdx == -1) {
          shortString = fullString.trim(); 
        }
        else {
          int lastLooked = 0;
          do  {
            int nextLineIdx = fullString.indexOf("\n", lastLooked);
            if (nextLineIdx > lastLooked) {
              shortString = fullString.substring(lastLooked, nextLineIdx).trim() + " ...";
              break;
            }
            else if (nextLineIdx == -1) {
              shortString = " ...";
              break;
            }
            lastLooked = nextLineIdx + 1;
          } while (true);
        }
        items.add(new Item(i ++, shortString, fullString));
      }
    }
    myAllContents = contents;
    FilteringListModel listModel = (FilteringListModel)myList.getModel();
    ((CollectionListModel)listModel.getOriginalModel()).removeAll();
    listModel.addAll(items);
    ListWithFilter listWithFilter = UIUtil.getParentOfType(ListWithFilter.class, myList);
    if (listWithFilter != null) {
      listWithFilter.getSpeedSearch().update();
      if (listModel.getSize() == 0) listWithFilter.resetFilter();
    }
  }

  protected abstract String getStringRepresentationFor(final Data content);

  protected abstract List<Data> getContents();

  public int getSelectedIndex() {
    Object o = myList.getSelectedValue();
    return o == null? -1 : ((Item)o).index;
  }
  
  public void setSelectedIndex(int index) {
    myList.setSelectedIndex(index);
    ScrollingUtil.ensureIndexIsVisible(myList, index, 0);
    updateViewerForSelection();
  }

  @NotNull
  public int[] getSelectedIndices() {
    Object[] values = myList.getSelectedValues();
    int[] result = new int[values.length];
    for (int i = 0, length = values.length; i < length; i++) {
      result[i] = ((Item)values[i]).index;
    }
    return result;
  }

  public List<Data> getAllContents() {
    return myAllContents;
  }

  @NotNull
  public String getSelectedText() {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Object o : myList.getSelectedValues()) {
      if (first) first = false;
      else sb.append("\n");
      String s = ((Item)o).longText;
      sb.append(StringUtil.convertLineSeparators(s));
    }
    return sb.toString();
  }
  
  private class MyListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setIcon(myListEntryIcon);
      if (myUseIdeaEditor) {
        int max = list.getModel().getSize();
        String indexString = String.valueOf(index + 1);
        int count = String.valueOf(max).length() - indexString.length();
        char[] spaces = new char[count];
        Arrays.fill(spaces, ' ');
        String prefix = indexString + new String(spaces) + "  ";
        append(prefix, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (UIUtil.isUnderGTKLookAndFeel()) {
        // Fix GTK background
        Color background = selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
        UIUtil.changeBackGround(this, background);
      }
      String text = ((Item)value).shortText;

      FontMetrics metrics = list.getFontMetrics(list.getFont());
      int charWidth = metrics.charWidth('m');
      int maxLength = list.getParent().getParent().getWidth() * 3 / charWidth / 2;
      text = StringUtil.first(text, maxLength, true); // do not paint long strings
      append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
  
  private static class Item {
    final int index;
    final String shortText;
    final String longText;

    private Item(int index, String shortText, String longText) {
      this.index = index;
      this.shortText = shortText;
      this.longText = longText;
    }
  }
}
