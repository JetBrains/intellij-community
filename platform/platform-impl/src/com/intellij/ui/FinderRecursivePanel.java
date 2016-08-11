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
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @param <T> List item type. Must implement {@code equals()/hashCode()} correctly.
 * @since 13.0
 */
public abstract class FinderRecursivePanel<T> extends JBSplitter implements DataProvider, Disposable {

  @NotNull
  private final Project myProject;

  @Nullable
  private final String myGroupId;

  @Nullable
  private final FinderRecursivePanel myParent;

  @Nullable
  private JComponent myChild = null;

  protected JBList myList;
  protected final CollectionListModel<T> myListModel = new CollectionListModel<>();

  private final MergingUpdateQueue myMergingUpdateQueue = new MergingUpdateQueue("FinderRecursivePanel", 100, true, this, this);
  private volatile boolean isMergeListItemsRunning;

  private final AtomicBoolean myUpdateSelectedPathModeActive = new AtomicBoolean();

  private final CopyProvider myCopyProvider = new CopyProvider() {
    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      final T value = getSelectedValue();
      CopyPasteManager.getInstance().setContents(new StringSelection(getItemText(value)));
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return getSelectedValue() != null;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return false;
    }
  };

  protected FinderRecursivePanel(@NotNull FinderRecursivePanel parent) {
    this(parent.getProject(), parent, parent.getGroupId());
  }

  protected FinderRecursivePanel(@NotNull Project project, @Nullable String groupId) {
    this(project, null, groupId);
  }

  protected FinderRecursivePanel(@NotNull Project project,
                                 @Nullable FinderRecursivePanel parent,
                                 @Nullable String groupId) {
    super(false, 0f);

    myProject = project;
    myParent = parent;
    myGroupId = groupId;

    if (myParent != null) {
      Disposer.register(myParent, this);
    }
  }

  public void init() {
    initWithoutUpdatePanel();
    updatePanel();
  }

  private void initWithoutUpdatePanel() {
    setFirstComponent(createLeftComponent());
    setSecondComponent(createDefaultRightComponent());

    if (getGroupId() != null) {
      setAndLoadSplitterProportionKey(getGroupId() + "[" + getIndex() + "]");
    }
    setDividerWidth(3);
    setShowDividerIcon(false);
    setShowDividerControls(true);
  }

  /**
   * Called in read action.
   *
   * @return Items for list.
   */
  @NotNull
  protected abstract List<T> getListItems();

  protected String getListEmptyText() {
    return "No entries";
  }

  @NotNull
  protected abstract String getItemText(T t);

  @Nullable
  protected Icon getItemIcon(T t) {
    return null;
  }

  protected abstract boolean hasChildren(T t);

  /**
   * To determine item list background color (if enabled).
   *
   * @param t Current item.
   * @return Containing file.
   */
  @Nullable
  protected VirtualFile getContainingFile(T t) {
    return null;
  }

  protected boolean isEditable() {
    return getSelectedValue() != null;
  }

  @Nullable
  protected JComponent createRightComponent(T t) {
    return new JPanel();
  }

  @Nullable
  protected JComponent createDefaultRightComponent() {
    return new JBPanelWithEmptyText().withEmptyText("Nothing selected");
  }

  protected JComponent createLeftComponent() {
    myList = createList();

    final JScrollPane pane =
      ScrollPaneFactory.createScrollPane(myList,
                                         ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return ListWithFilter.wrap(myList, pane, new Function<T, String>() {
      @Override
      public String fun(T o) {
        return getItemText(o);
      }
    });
  }

  protected JBList createList() {
    final JBList list = new JBList(myListModel);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setEmptyText(getListEmptyText());
    list.setCellRenderer(createListCellRenderer());

    installListActions(list);
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) return;
        if (isMergeListItemsRunning()) return;
        if (myUpdateSelectedPathModeActive.get()) return;
        updateRightComponent(true);
      }
    });
    ScrollingUtil.installActions(list);

    //    installSpeedSearch(list); // TODO

    installEditOnDoubleClick(list);
    return list;
  }

  private void handleGotoPrevious() {
    IdeFocusManager.getInstance(myProject).requestFocus(myList, true);
  }

  private void handleGotoNext() {
    if (!myList.isEmpty()) {
      if (myList.getSelectedValue() == null) {
        myList.setSelectedIndex(0);
        updateRightComponent(true);
      }
    }
    IdeFocusManager.getInstance(myProject).requestFocus(myList, true);
  }

  private void installListActions(JBList list) {
    AnAction previousPanelAction = new AnAction("Previous", null, AllIcons.Actions.Back) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(!isRootPanel());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        assert myParent != null;
        myParent.handleGotoPrevious();
      }
    };
    previousPanelAction.registerCustomShortcutSet(KeyEvent.VK_LEFT, 0, list);

    AnAction nextPanelAction = new AnAction("Next", null, AllIcons.Actions.Forward) {
      @Override
      public void update(AnActionEvent e) {
        final T value = getSelectedValue();
        e.getPresentation().setEnabled(value != null &&
                                       hasChildren(value) &&
                                       getSecondComponent() instanceof FinderRecursivePanel);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        FinderRecursivePanel finderRecursivePanel = (FinderRecursivePanel)getSecondComponent();
        finderRecursivePanel.handleGotoNext();
      }
    };
    nextPanelAction.registerCustomShortcutSet(KeyEvent.VK_RIGHT, 0, list);

    AnAction editAction = new AnAction("Edit", null, AllIcons.Actions.Edit) {

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(isEditable());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        performEditAction();
      }
    };
    editAction.registerCustomShortcutSet(CommonShortcuts.ENTER, list);

    AnAction[] actions = new AnAction[]{
      previousPanelAction,
      nextPanelAction,
      Separator.getInstance(),
      editAction};

    final AnAction[] customActions = getCustomListActions();
    if (customActions.length > 0) {
      actions = ArrayUtil.append(actions, Separator.getInstance());
      actions = ArrayUtil.mergeArrays(actions, customActions);
    }

    ActionGroup contextActionGroup = new DefaultActionGroup(actions);
    PopupHandler.installUnknownPopupHandler(list, contextActionGroup, ActionManager.getInstance());
  }

  protected AnAction[] getCustomListActions() {
    return AnAction.EMPTY_ARRAY;
  }

  private void installSpeedSearch(JBList list) {
    final ListSpeedSearch search = new ListSpeedSearch(list, new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        //noinspection unchecked
        return getItemText((T)o);
      }
    });
    search.setComparator(new SpeedSearchComparator(false));
  }

  private void installEditOnDoubleClick(JBList list) {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        performEditAction();
        return true;
      }
    }.installOn(list);
  }

  protected boolean performEditAction() {
    Navigatable data = CommonDataKeys.NAVIGATABLE.getData(DataManager.getInstance().getDataContext(myList));
    if (data != null && data.canNavigate()) {
      data.navigate(true);
    }
    return false;
  }

  protected ListCellRenderer createListCellRenderer() {
    return new ColoredListCellRenderer() {

      private final FileColorManager myFileColorManager = FileColorManager.getInstance(getProject());

      public Component getListCellRendererComponent(JList list,
                                                    Object value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        mySelected = isSelected;
        myForeground = UIUtil.getTreeTextForeground();
        mySelectionForeground = cellHasFocus ? list.getSelectionForeground() : UIUtil.getTreeTextForeground();

        clear();
        setFont(UIUtil.getListFont());

        //noinspection unchecked
        final T t = (T)value;
        try {
          setIcon(getItemIcon(t));
          append(getItemText(t));
        }
        catch (IndexNotReadyException e) {
          append("loading...");
        }

        try {
          doCustomizeCellRenderer(this, list, t, index, isSelected, cellHasFocus);
        }
        catch (IndexNotReadyException ignored) {
          // ignore
        }

        Color bg = isSelected ? UIUtil.getTreeSelectionBackground(cellHasFocus) : UIUtil.getTreeTextBackground();
        if (!isSelected && myFileColorManager.isEnabled()) {
          final Color fileBgColor = myFileColorManager.getRendererBackground(getContainingFile(t));
          bg = fileBgColor == null ? bg : fileBgColor;
        }
        setBackground(bg);

        if (hasChildren(t)) {
          JPanel result = new JPanel(new BorderLayout(0, 0));
          JLabel childrenLabel = new JLabel();
          childrenLabel.setOpaque(true);
          childrenLabel.setVisible(true);
          childrenLabel.setBackground(bg);

          final boolean isDark = ColorUtil.isDark(UIUtil.getListSelectionBackground());
          childrenLabel.setIcon(isSelected ? isDark ? AllIcons.Icons.Ide.NextStepInverted
                                                    : AllIcons.Icons.Ide.NextStep
                                           : AllIcons.Icons.Ide.NextStepGrayed);
          result.add(this, BorderLayout.CENTER);
          result.add(childrenLabel, BorderLayout.EAST);
          return result;
        }
        return this;
      }

      @Override
      protected final void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      }
    };
  }

  protected void doCustomizeCellRenderer(SimpleColoredComponent comp, JList list, T value, int index, boolean selected, boolean hasFocus) {
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    Object selectedValue = getSelectedValue();
    if (selectedValue == null) return null;

    if (CommonDataKeys.PSI_ELEMENT.is(dataId) && selectedValue instanceof PsiElement) {
      return selectedValue;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId) && selectedValue instanceof Navigatable) {
      return selectedValue;
    }
    if (LangDataKeys.MODULE.is(dataId) && selectedValue instanceof Module) {
      return selectedValue;
    }

    if (selectedValue instanceof DataProvider) {
      return ((DataProvider)selectedValue).getData(dataId);
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyProvider;
    }
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    myMergingUpdateQueue.cancelAllUpdates();
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public T getSelectedValue() {
    return (T)myList.getSelectedValue();
  }

  /**
   * Performs recursive update selecting given values.
   *
   * @param pathToSelect Values to select.
   * @since 14
   */
  public void updateSelectedPath(Object... pathToSelect) {
    if (!myUpdateSelectedPathModeActive.compareAndSet(false, true)) return;
    FinderRecursivePanel panel = this;
    for (int i = 0; i < pathToSelect.length; i++) {
      Object selectedValue = pathToSelect[i];
      panel.setSelectedValue(selectedValue);
      if (i < pathToSelect.length - 1) {
        final JComponent component = panel.getSecondComponent();
        assert component instanceof FinderRecursivePanel : Arrays.toString(pathToSelect);
        panel = (FinderRecursivePanel)component;
      }
    }

    IdeFocusManager.getInstance(myProject).requestFocus(panel.myList, true);

    myUpdateSelectedPathModeActive.set(false);
  }

  private void setSelectedValue(final Object value) {
    if (value.equals(myList.getSelectedValue())) {
      return;
    }

    // load list items synchronously
    myList.setPaintBusy(true);
    try {
      final List<T> listItems = ApplicationManager.getApplication().runReadAction(new Computable<List<T>>() {
        @Override
        public List<T> compute() {
          return getListItems();
        }
      });
      mergeListItems(myListModel, myList, listItems);
    }
    finally {
      myList.setPaintBusy(false);
    }

    myList.setSelectedValue(value, true);

    // always recreate since instance might depend on this one's selected value
    createRightComponent(false);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public FinderRecursivePanel getParentPanel() {
    return myParent;
  }

  @Nullable
  protected String getGroupId() {
    return myGroupId;
  }

  public void updatePanel() {
    if (myUpdateSelectedPathModeActive.get()) {
      return;
    }

    myList.setPaintBusy(true);
    myMergingUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        final T oldValue = getSelectedValue();
        final int oldIndex = myList.getSelectedIndex();

        ApplicationManager.getApplication().executeOnPooledThread(() -> DumbService.getInstance(getProject()).runReadActionInSmartMode(() -> {
          try {
            final List<T> listItems = getListItems();

            SwingUtilities.invokeLater(() -> {
              mergeListItems(myListModel, myList, listItems);

              if (myList.isEmpty()) {
                createRightComponent(true);
              }
              else if (myList.getSelectedIndex() < 0) {
                myList.setSelectedIndex(myListModel.getSize() > oldIndex ? oldIndex : 0);
              }
              else {
                Object newValue = myList.getSelectedValue();
                updateRightComponent(oldValue == null || !oldValue.equals(newValue) || myList.isEmpty());
              }
            });
          }
          finally {
            myList.setPaintBusy(false);
          }
        }));
      }
    });
  }

  protected void mergeListItems(@NotNull CollectionListModel<T> listModel, @NotNull JList list, @NotNull List<T> newItems) {
    setMergeListItemsRunning(true);

    try {
      if (listModel.getSize() == 0) {
        listModel.add(newItems);
      }
      else if (newItems.size() == 0) {
        listModel.removeAll();
      }
      else {

        int newSelectedIndex = -1;

        T selection = (T)list.getSelectedValue();
        if (selection != null) {
          newSelectedIndex = newItems.indexOf(selection);
        }


        listModel.removeAll();
        listModel.add(newItems);

        list.setSelectedIndex(newSelectedIndex);
      }
    }
    finally {
      setMergeListItemsRunning(false);
    }
  }

  public boolean isMergeListItemsRunning() {
    return isMergeListItemsRunning;
  }

  protected void setMergeListItemsRunning(boolean isListMergeRunning) {
    this.isMergeListItemsRunning = isListMergeRunning;
  }

  public void updateRightComponent(boolean force) {
    if (force) {
      createRightComponent(true);
    }
    else if (myChild instanceof FinderRecursivePanel) {
      ((FinderRecursivePanel)myChild).updatePanel();
    }
  }

  private void createRightComponent(boolean withUpdatePanel) {
    if (myChild instanceof Disposable) {
      Disposer.dispose((Disposable)myChild);
    }
    T value = getSelectedValue();
    if (value != null) {
      myChild = createRightComponent(value);
      if (myChild instanceof FinderRecursivePanel) {
        final FinderRecursivePanel childPanel = (FinderRecursivePanel)myChild;
        if (withUpdatePanel) {
          childPanel.init();
        }
        else {
          childPanel.initWithoutUpdatePanel();
        }
      }
    }
    else {
      myChild = createDefaultRightComponent();
    }
    setSecondComponent(myChild);
  }

  private int getIndex() {
    int index = 0;
    FinderRecursivePanel parent = myParent;
    while (parent != null) {
      index++;
      parent = parent.getParentPanel();
    }

    return index;
  }

  protected boolean isRootPanel() {
    return getParentPanel() == null;
  }

  @Override
  public void doLayout() {
    if (myProportion == 0) {
      int total = getOrientation() ? getHeight() : getWidth();
      float proportion = (float)getFirstComponentPreferredSize() / (total - getDividerWidth());
      if (proportion > .0f && proportion < 1.0f) {
        setProportion(proportion);
      }
    }

    super.doLayout();
  }

  protected int getFirstComponentPreferredSize() {
    return 200;
  }
}