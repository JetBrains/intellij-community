/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.diff.BackgroundOperatingDiffElement;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffModel;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableModel extends AbstractTableModel implements DirDiffModel, Disposable {
  private static final Logger LOG = Logger.getInstance("#"+DirDiffTableModel.class.getName());
  public static final String COLUMN_NAME = "Name";
  public static final String COLUMN_SIZE = "Size";
  public static final String COLUMN_DATE = "Date";
  private final Project myProject;
  private final DirDiffSettings mySettings;
  private DiffElement mySrc;
  private DiffElement myTrg;
  private DTree myTree;
  private final List<DirDiffElement> myElements = new ArrayList<DirDiffElement>();
  private final AtomicBoolean myUpdating = new AtomicBoolean(false);
  private JBTable myTable;
  public String DECORATOR = "DIFF_TABLE_DECORATOR";
  public volatile AtomicReference<String> text = new AtomicReference<String>(prepareText(""));
  private Updater updater;
  private List<DirDiffModelListener> myListeners = new ArrayList<DirDiffModelListener>();
  private TableSelectionConfig mySelectionConfig;

  public static final String EMPTY_STRING = "                                                  ";
  private DirDiffPanel myPanel;

  public DirDiffTableModel(Project project, DiffElement src, DiffElement trg, DirDiffSettings settings) {
    myProject = project;
    mySettings = settings;
    mySrc = src;
    myTrg = trg;
  }

  public void stopUpdating() {
    if (myUpdating.get()) {
      myUpdating.set(false);
    }
  }

  public void applyRemove() {
    final List<DirDiffElement> selectedElements = getSelectedElements();
    myUpdating.set(true);
    final Iterator<DirDiffElement> i = myElements.iterator();
    while(i.hasNext()) {
      final DType type = i.next().getType();
      switch (type) {
        case SOURCE:
          if (!mySettings.showNewOnSource) i.remove();
          break;
        case TARGET:
          if (!mySettings.showNewOnTarget) i.remove();
          break;
        case SEPARATOR:
          break;
        case CHANGED:
          if (!mySettings.showDifferent) i.remove();
          break;
        case EQUAL:
          if (!mySettings.showEqual) i.remove();
          break;
        case ERROR:
      }
    }

    boolean sep = true;
    for (int j = myElements.size() - 1; j >= 0; j--) {
      if (myElements.get(j).isSeparator()) {
        if (sep) {
          myElements.remove(j);
        }
        else {
          sep = true;
        }
      }
      else {
        sep = false;
      }
    }
    fireTableDataChanged();
    myUpdating.set(false);
    int index;
    if (!selectedElements.isEmpty() && (index = myElements.indexOf(selectedElements.get(0))) != -1) {
      myTable.getSelectionModel().setSelectionInterval(index, index);
      TableUtil.scrollSelectionToVisible(myTable);
    }
    else {
      selectFirstRow();
    }
    myPanel.focusTable();
    myPanel.update(true);
  }

  public void selectFirstRow() {
    if (myElements.size() > 0) {
      int row = myElements.get(0).isSeparator() ? 1 : 0;
      if (row < myTable.getRowCount()) {
        myTable.getSelectionModel().setSelectionInterval(row, row);
        TableUtil.scrollSelectionToVisible(myTable);
      }
    }
  }

  public void setPanel(DirDiffPanel panel) {
    myPanel = panel;
  }

  public void updateFromUI() {
    getSettings().setFilter(myPanel.getFilter());
  }

  public boolean isOperationsEnabled() {
    return mySrc.isOperationsEnabled() && myTrg.isOperationsEnabled();
  }

  public List<DirDiffElement> getElements() {
    return myElements;
  }

  private static String prepareText(String text) {
    final int LEN = EMPTY_STRING.length();
    String right;
    if (text == null) {
      right = EMPTY_STRING;
    }
    else if (text.length() == LEN) {
      right = text;
    }
    else if (text.length() < LEN) {
      right = text + EMPTY_STRING.substring(0, LEN - text.length());
    }
    else {
      right = "..." + text.substring(text.length() - LEN + 2);
    }
    return "Loading... " + right;
  }

  void fireUpdateStarted() {
    for (DirDiffModelListener listener : myListeners) {
      listener.updateStarted();
    }
  }

  void fireUpdateFinished() {
    for (DirDiffModelListener listener : myListeners) {
      listener.updateFinished();
    }
  }

  void addModelListener(DirDiffModelListener listener) {
    myListeners.add(listener);
  }

  public void reloadModel(final boolean userForcedRefresh) {
    myUpdating.set(true);
    final JBLoadingPanel loadingPanel = getLoadingPanel();
    loadingPanel.startLoading();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          updater = new Updater(loadingPanel, 100);
          updater.start();
          myTree = new DTree(null, "", true);
          mySrc.refresh(userForcedRefresh);
          myTrg.refresh(userForcedRefresh);
          scan(mySrc, myTree, true);
          scan(myTrg, myTree, false);
        }
        catch (final IOException e) {
          LOG.warn(e);
          reportException(VcsBundle.message("refresh.failed.message", StringUtil.decapitalize(e.getLocalizedMessage())));
        }
        finally {
          myTree.setSource(mySrc);
          myTree.setTarget(myTrg);
          myTree.update(mySettings);
          applySettings();
        }
      }
    });
  }

  private void reportException(final String htmlContent) {
    Runnable balloonShower = new Runnable() {
      @Override
      public void run() {
        Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(htmlContent, MessageType.WARNING, null).
          setShowCallout(false).setHideOnClickOutside(true).setHideOnAction(true).setHideOnFrameResize(true).setHideOnKeyOutside(true).
          createBalloon();
        final Rectangle rect = myPanel.getPanel().getBounds();
        final Point p = new Point(rect.x + rect.width - 100, rect.y + 50);
        final RelativePoint point = new RelativePoint(myPanel.getPanel(), p);
        balloon.show(point, Balloon.Position.below);
        Disposer.register(myProject != null ? myProject : ApplicationManager.getApplication(), balloon);
      }
    };
    ApplicationManager.getApplication().invokeLater(balloonShower, new Condition() {
      @Override
      public boolean value(Object o) {
        return !(myProject == null || myProject.isDefault()) && ((!myProject.isOpen()) || myProject.isDisposed());
      }
    }
    );
  }

  private JBLoadingPanel getLoadingPanel() {
    return (JBLoadingPanel)myTable.getClientProperty(DECORATOR);
  }

  public void applySettings() {
    if (! myUpdating.get()) myUpdating.set(true);
    final JBLoadingPanel loadingPanel = getLoadingPanel();
    if (!loadingPanel.isLoading()) {
      loadingPanel.startLoading();
      if (updater == null) {
        updater = new Updater(loadingPanel, 100);
        updater.start();
      }
    }
    final Application app = ApplicationManager.getApplication();
    app.executeOnPooledThread(new Runnable() {
      public void run() {
        myTree.updateVisibility(mySettings);
        final ArrayList<DirDiffElement> elements = new ArrayList<DirDiffElement>();
        fillElements(myTree, elements);
        final Runnable uiThread = new Runnable() {
          public void run() {
            clear();
            myElements.addAll(elements);
            myUpdating.set(false);
            fireTableDataChanged();
            DirDiffTableModel.this.text.set("");
            if (loadingPanel.isLoading()) {
              loadingPanel.stopLoading();
            }
            if (mySelectionConfig == null) {
              selectFirstRow();
            } else {
              mySelectionConfig.restore();
            }
            myPanel.update(true);
          }
        };
        if (myProject.isDefault()) {
          SwingUtilities.invokeLater(uiThread);
        } else {
          app.invokeLater(uiThread);
        }
      }
    });
  }

  private void fillElements(DTree tree, List<DirDiffElement> elements) {
    if (!myUpdating.get()) return;
    boolean separatorAdded = tree.getParent() == null;
    text.set(prepareText(tree.getPath()));
    for (DTree child : tree.getChildren()) {
      if (!myUpdating.get()) return;
      if (!child.isContainer()) {
        if (child.isVisible()) {
          if (!separatorAdded) {
            elements.add(DirDiffElement.createDirElement(tree, tree.getSource(), tree.getTarget(), tree.getPath()));
            separatorAdded = true;
          }
          final DType type = child.getType();
          if (type != null) {
            switch (type) {
              case SOURCE:
                elements.add(DirDiffElement.createSourceOnly(child, child.getSource()));
                break;
              case TARGET:
                elements.add(DirDiffElement.createTargetOnly(child, child.getTarget()));
                break;
              case CHANGED:
                elements.add(DirDiffElement.createChange(child, child.getSource(), child.getTarget()));
                break;
              case EQUAL:
                elements.add(DirDiffElement.createEqual(child, child.getSource(), child.getTarget()));
                break;
              case ERROR:
                elements.add(DirDiffElement.createError(child, child.getSource(), child.getTarget()));
              case SEPARATOR:
                break;
            }
          } else {
            LOG.error(String.format("Element's type is null [Name: %s, Container: %s, Source: %s, Target: %s] ",
                                   child.getName(), child.isContainer(), child.getSource(), child.getTarget()));
          }
        }
      } else {
        fillElements(child, elements);
      }
    }
  }

  public void clear() {
    if (!myElements.isEmpty()) {
      final int size = myElements.size();
      myElements.clear();
      fireTableRowsDeleted(0, size - 1);
    }
  }

  private void scan(DiffElement element, DTree root, boolean source) throws IOException {
    if (!myUpdating.get()) return;
    if (element.isContainer()) {
      text.set(prepareText(element.getPath()));
      final DiffElement[] children = element.getChildren();
      for (DiffElement child : children) {
        if (!myUpdating.get()) return;
        final DTree el = root.addChild(child, source);
        scan(child, el, source);
      }
    }
  }

  public String getTitle() {
    return IdeBundle.message("diff.dialog.title", mySrc.getPresentablePath(), myTrg.getPresentablePath());
  }

  @Nullable
  public DirDiffElement getElementAt(int index) {
    return 0 <= index && index < myElements.size() ? myElements.get(index) : null;
  }

  public DiffElement getSourceDir() {
    return mySrc;
  }

  public DiffElement getTargetDir() {
    return myTrg;
  }

  public void setSourceDir(DiffElement src) {
    mySrc = src;
  }

  public void setTargetDir(DiffElement trg) {
    myTrg = trg;
  }

  @Override
  public int getRowCount() {
    return myElements.size();
  }

  @Override
  public int getColumnCount() {
    int count = 3;
    if (mySettings.showDate) count += 2;
    if (mySettings.showSize) count += 2;
    return count;
  }

  public JBTable getTable() {
    return myTable;
  }

  public void setTable(JBTable table) {
    myTable = table;
  }

  @Nullable
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final DirDiffElement element = myElements.get(rowIndex);
    if (element.isSeparator()) {
      return columnIndex == 0 ? element.getName() : null;
    }

    final String name = getColumnName(columnIndex);
    boolean isSrc = columnIndex < getColumnCount() / 2;
    if (name.equals(COLUMN_NAME)) {
      return isSrc ? element.getSourceName() : element.getTargetName();
    } else if (name.equals(COLUMN_SIZE)) {
      return isSrc ? element.getSourceSize() : element.getTargetSize();
    } else  if (name.equals(COLUMN_DATE)) {
      return isSrc ? element.getSourceModificationDate() : element.getTargetModificationDate();
    }
    return "";
  }

  public List<DirDiffElement> getSelectedElements() {
    final int[] rows = myTable.getSelectedRows();
    final ArrayList<DirDiffElement> elements = new ArrayList<DirDiffElement>();
    for (int row : rows) {
      final DirDiffElement element = getElementAt(row);
      if (element == null || element.isSeparator()) continue;
      elements.add(element);
    }
    return elements;
  }

  @Override
  public String getColumnName(int column) {
    final int count = (getColumnCount() - 1) / 2;
    if (column == count) return "*";
    if (column > count) {
      column = getColumnCount() - 1 - column;
    }
    switch (column) {
      case 0: return COLUMN_NAME;
      case 1: return mySettings.showSize ? COLUMN_SIZE : COLUMN_DATE;
      case 2: return COLUMN_DATE;
    }
    return "";
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isShowEqual() {
    return mySettings.showEqual;
  }

  public void setShowEqual(boolean show) {
    mySettings.showEqual = show;
  }

  public boolean isShowDifferent() {
    return mySettings.showDifferent;
  }

  public void setShowDifferent(boolean show) {
    mySettings.showDifferent = show;
  }

  public boolean isShowNewOnSource() {
    return mySettings.showNewOnSource;
  }

  public void setShowNewOnSource(boolean show) {
    mySettings.showNewOnSource = show;
  }

  public boolean isShowNewOnTarget() {
    return mySettings.showNewOnTarget;
  }

  public void setShowNewOnTarget(boolean show) {
    mySettings.showNewOnTarget = show;
  }

  public boolean isUpdating() {
    return myUpdating.get();
  }

  public DirDiffSettings.CompareMode getCompareMode() {
    return mySettings.compareMode;
  }

  public void setCompareMode(DirDiffSettings.CompareMode mode) {
    mySettings.compareMode = mode;
  }

  @Override
  public void dispose() {
    myListeners.clear();
    myElements.clear();
    mySrc = null;
    myTrg = null;
    myTree = null;
  }

  public DirDiffSettings getSettings() {
    return mySettings;
  }

  public void performCopyTo(final DirDiffElement element) {
    final DiffElement<?> source = element.getSource();
    if (source != null) {
      final String path = element.getParentNode().getPath();

      if (source instanceof BackgroundOperatingDiffElement) {
        final Ref<String> errorMessage = new Ref<String>();
        final Ref<DiffElement> diff = new Ref<DiffElement>();
        Runnable onFinish = new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().assertIsDispatchThread();
            if (!Disposer.isDisposed(DirDiffTableModel.this)) {
              DiffElement newElement = diff.get();
              if (newElement == null && element.getTarget() != null) {
                final int row = myElements.indexOf(element);
                element.updateTargetData();
                fireTableRowsUpdated(row, row);
              }
              refreshElementAfterCopyTo(newElement, element);
              if (!errorMessage.isNull()) {
                reportException(errorMessage.get());
              }
            }
          }
        };
        ((BackgroundOperatingDiffElement)source).copyTo(myTrg, errorMessage, diff, onFinish, element.getTarget(), path);
      }
      else {
        final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
        try {
          final DiffElement<?> diffElement = source.copyTo(myTrg, path);
          refreshElementAfterCopyTo(diffElement, element);
        }
        finally {
          token.finish();
        }
      }
    }
  }

  private void refreshElementAfterCopyTo(DiffElement newElement, DirDiffElement element) {
    if (newElement != null) {
      final DTree node = element.getNode();
      node.setType(DType.EQUAL);
      node.setTarget(newElement);

      final int row = myElements.indexOf(element);
      if (getSettings().showEqual) {
        element.updateSourceFromTarget(newElement);
        fireTableRowsUpdated(row, row);
      }
      else {
        removeElement(element, false);
      }
    }
  }

  public void performCopyFrom(final DirDiffElement element) {
    final DiffElement<?> target = element.getTarget();
    if (target != null) {
      final String path = element.getParentNode().getPath();

      if (target instanceof BackgroundOperatingDiffElement) {
        final Ref<String> errorMessage = new Ref<String>();
        final Ref<DiffElement> diff = new Ref<DiffElement>();
        Runnable onFinish = new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().assertIsDispatchThread();
            if (!Disposer.isDisposed(DirDiffTableModel.this)) {
              refreshElementAfterCopyFrom(element, diff.get());
              if (!errorMessage.isNull()) {
                reportException(errorMessage.get());
              }
            }
          }
        };
        ((BackgroundOperatingDiffElement)target).copyTo(mySrc, errorMessage, diff, onFinish, element.getSource(), path);
      }
      else {
        final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
        try {
          final DiffElement<?> diffElement = target.copyTo(mySrc, path);
          refreshElementAfterCopyFrom(element, diffElement);
        }
        finally {
          token.finish();
        }
      }
    }
  }

  private void refreshElementAfterCopyFrom(DirDiffElement element, DiffElement newElement) {
    if (newElement != null) {
      final DTree node = element.getNode();
      node.setType(DType.EQUAL);
      node.setSource(newElement);

      final int row = myElements.indexOf(element);
      if (getSettings().showEqual) {
        element.updateTargetFromSource(newElement);
        fireTableRowsUpdated(row, row);
      }
      else {
        removeElement(element, false);
      }
    }
  }

  private void removeElement(DirDiffElement element, boolean removeFromTree) {
    int row = myElements.indexOf(element);
    if (row != -1) {
      final DTree node = element.getNode();
      if (removeFromTree) {
        final DTree parentNode = element.getParentNode();
        parentNode.remove(node);
      }
      myElements.remove(row);
      int start = row;

      if (row > 0 && row == myElements.size() && myElements.get(row - 1).isSeparator()) {
        final DirDiffElement el = myElements.get(row - 1);
        if (removeFromTree) {
          el.getParentNode().remove(el.getNode());
        }
        myElements.remove(row - 1);
        start = row - 1;
        }
        else if (row != myElements.size() && myElements.get(row).isSeparator() && row > 0 && myElements.get(row - 1).isSeparator()) {
          final DirDiffElement el = myElements.get(row - 1);
          if (removeFromTree) {
            el.getParentNode().remove(el.getNode());
          }
          myElements.remove(row - 1);
          start = row - 1;
        }
        fireTableRowsDeleted(start, row);
      }
  }

  public void performDelete(final DirDiffElement element) {
    final DiffElement source = element.getSource();
    final DiffElement target = element.getTarget();
    LOG.assertTrue(source == null || target == null);
    if (source instanceof BackgroundOperatingDiffElement || target instanceof BackgroundOperatingDiffElement) {
      final Ref<String> errorMessage = new Ref<String>();
      Runnable onFinish = new Runnable() {
        @Override
        public void run() {
          if (!Disposer.isDisposed(DirDiffTableModel.this)) {
            if (!errorMessage.isNull()) {
              reportException(errorMessage.get());
            }
            else {
              if (myElements.indexOf(element) != -1) {
                removeElement(element, true);
              }
            }
          }
        }
      };
      if (source != null) {
        ((BackgroundOperatingDiffElement)source).delete(errorMessage, onFinish);
      }
      else {
        ((BackgroundOperatingDiffElement)target).delete(errorMessage, onFinish);
      }
    }
    else {
      if (myElements.indexOf(element) != -1) {
        removeElement(element, true);
      }
      final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
      try {
        if (source != null) {
          source.delete();
        }
        if (target != null) {
          target.delete();
        }
      }
      finally {
        token.finish();
      }
    }
  }

  public void synchronizeSelected() {
    rememberSelection();
    for (DirDiffElement element : getSelectedElements()) {
      syncElement(element);
    }
    restoreSelection();
 }

  private void restoreSelection() {
    if (mySelectionConfig != null) {
      mySelectionConfig.restore();
    }
  }

  public void synchronizeAll() {
    for (DirDiffElement element : myElements.toArray(new DirDiffElement[myElements.size()])) {
      syncElement(element);
    }
    selectFirstRow();
  }

  private void syncElement(DirDiffElement element) {
    final DirDiffOperation operation = element.getOperation();
    if (operation == null) return;
    switch (operation) {
      case COPY_TO:
        performCopyTo(element);
        break;
      case COPY_FROM:
        performCopyFrom(element);
        break;
      case MERGE:
        break;
      case EQUAL:
        break;
      case NONE:
        break;
      case DELETE:
        performDelete(element);
        break;
    }
  }

  class Updater extends Thread {
    private final JBLoadingPanel myLoadingPanel;
    private final int mySleep;

    Updater(JBLoadingPanel loadingPanel, int sleep) {
      super("Loading Updater");
      myLoadingPanel = loadingPanel;
      mySleep = sleep;
    }

    @Override
    public void run() {
      if (myLoadingPanel.isLoading()) {
        try {
          Thread.sleep(mySleep);
        }
        catch (InterruptedException e) {//
        }
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final String s = text.get();
            if (s != null && myLoadingPanel.isLoading()) {
              myLoadingPanel.setLoadingText(s);
            }
          }
        });
        updater = new Updater(myLoadingPanel, mySleep);
        updater.start();
      } else {
        updater = null;
        myPanel.focusTable();
      }
    }
  }

  public void rememberSelection() {
    mySelectionConfig = new TableSelectionConfig();
  }

  public class TableSelectionConfig {
    private final int selectedRow;
    private final int rowCount;
    TableSelectionConfig() {
      selectedRow = myTable.getSelectedRow();
      rowCount = myTable.getRowCount();
    }

    void restore() {
      final int newRowCount = myTable.getRowCount();
      if (newRowCount == 0) return;

      int row = Math.min(newRowCount < rowCount ? selectedRow : selectedRow + 1, newRowCount - 1);
      final DirDiffElement element = getElementAt(row);
      if (element != null && element.isSeparator()) {
        if (getElementAt(row +1) != null) {
          row += 1;
        } else {
          row -= 1;
        }
      }
      final DirDiffElement el = getElementAt(row);
      row = el == null || el.isSeparator() ? 0 : row;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      TableUtil.scrollSelectionToVisible(myTable);
    }
  }
}
