/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
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
public abstract class FinderRecursivePanel<T> extends OnePixelSplitter implements DataProvider, UserDataHolder, Disposable {

  @NotNull
  private final Project myProject;

  @Nullable
  private final String myGroupId;

  @Nullable
  private final FinderRecursivePanel myParent;

  @Nullable
  private JComponent myChild = null;

  protected JBList<T> myList;
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

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

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

  public void initPanel() {
    initWithoutUpdatePanel();
    updatePanel();
  }

  private void initWithoutUpdatePanel() {
    setFirstComponent(createLeftComponent());
    setSecondComponent(createDefaultRightComponent());

    if (getGroupId() != null) {
      setAndLoadSplitterProportionKey(getGroupId() + "[" + getIndex() + "]");
    }
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

  /**
   * Returns tooltip text for the given list item or null if no tooltip is available.
   * <p>
   * <p>This method is invoked by panel's list cell render in order to set a tooltip text for the list cell render component.
   * It is invoked before {@link #doCustomizeCellRenderer(SimpleColoredComponent, JList, Object, int, boolean, boolean)},
   * thus the tooltip may still be reset in {@code doCustomizeCellRenderer}.
   *
   * @param t the list item
   * @return the text to display in a tooltip for the given list item
   * @since 2017.2
   */
  @Nullable
  protected String getItemTooltipText(T t) {
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
    return ListWithFilter.wrap(myList, pane, o -> getItemText(o));
  }

  protected JBList<T> createList() {
    final JBList<T> list = new JBList<>(myListModel);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setEmptyText(getListEmptyText());
    list.setCellRenderer(createListCellRenderer());

    if (hasFixedSizeListElements()) {
      list.setFixedCellHeight(JBUI.scale(UIUtil.LIST_FIXED_CELL_HEIGHT));
      list.setFixedCellWidth(list.getWidth());
    }

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
    //noinspection unchecked
    final ListSpeedSearch search = new ListSpeedSearch(list, (Function<Object, String>)o -> getItemText((T)o));
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

  protected ListCellRenderer<T> createListCellRenderer() {
    return new MyListCellRenderer();
  }

  protected void doCustomizeCellRenderer(SimpleColoredComponent comp, JList list, T value, int index, boolean selected, boolean hasFocus) {
  }

  /**
   * Whether this list contains "fixed size" elements.
   *
   * @return true.
   * @since 2017.2
   */
  protected boolean hasFixedSizeListElements() {
    return true;
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

    if (selectedValue instanceof DataProvider && (!(selectedValue instanceof ValidateableNode) || ((ValidateableNode)selectedValue).isValid())) {
      return ((DataProvider)selectedValue).getData(dataId);
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyProvider;
    }
    return null;
  }

  @Nullable
  @Override
  public <U> U getUserData(@NotNull Key<U> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <U> void putUserData(@NotNull Key<U> key, @Nullable U value) {
    myUserDataHolder.putUserData(key, value);
  }

  @Override
  public void dispose() {
    super.dispose();
    myMergingUpdateQueue.cancelAllUpdates();
  }

  /**
   * @return true if already disposed.
   * @since 2017.1
   */
  protected boolean isDisposed() {
    return Disposer.isDisposed(this);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public T getSelectedValue() {
    return myList.getSelectedValue();
  }

  /**
   * Performs recursive update selecting given values.
   *
   * @param pathToSelect Values to select.
   * @since 14
   */
  public void updateSelectedPath(Object... pathToSelect) {
    if (!myUpdateSelectedPathModeActive.compareAndSet(false, true)) return;
    try {
      FinderRecursivePanel panel = this;
      for (int i = 0; i < pathToSelect.length; i++) {
        Object selectedValue = pathToSelect[i];
        panel.setSelectedValue(selectedValue);

        if (i < pathToSelect.length - 1) {
          final JComponent component = panel.getSecondComponent();
          if (!(component instanceof FinderRecursivePanel)) {
            throw new IllegalStateException("failed to select idx=" + (i + 1) + ": " +
                                            "component=" + component + ", " +
                                            "pathToSelect=" + Arrays.toString(pathToSelect));
          }
          panel = (FinderRecursivePanel)component;
        }
      }

      IdeFocusManager.getInstance(myProject).requestFocus(panel.myList, true);
    }
    finally {
      myUpdateSelectedPathModeActive.set(false);
    }
  }

  private void setSelectedValue(final Object value) {
    if (value.equals(myList.getSelectedValue())) {
      return;
    }

    // load list items synchronously
    myList.setPaintBusy(true);
    try {
      final List<T> listItems = ReadAction.compute(() -> getListItems());
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

        ApplicationManager.getApplication()
          .executeOnPooledThread(() -> DumbService.getInstance(getProject()).runReadActionInSmartMode(() -> {
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

  protected void mergeListItems(@NotNull CollectionListModel<T> listModel, @NotNull JList<T> list, @NotNull List<T> newItems) {
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

        T selection = list.getSelectedValue();
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
          childPanel.initPanel();
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

  private class MyListCellRenderer extends ColoredListCellRenderer<T> {
    private static final String ITEM_PROPERTY = "FINDER_RECURSIVE_PANEL_ITEM_PROPERTY";

    @Override
    public String getToolTipText(MouseEvent event) {
      String toolTipText = getToolTipText();
      if (toolTipText != null) {
        return toolTipText;
      }
      @SuppressWarnings("unchecked")
      T value = (T)getClientProperty(ITEM_PROPERTY);
      return FinderRecursivePanel.this.getItemTooltipText(value);
    }

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
        putClientProperty(ITEM_PROPERTY, t);
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
      if (!isSelected) {
        VirtualFile file = getContainingFile(t);
        Color bgColor = file == null ? null : EditorTabbedContainer.calcTabColor(myProject, file);
        bg = bgColor == null ? bg : bgColor;
      }
      setBackground(bg);

      if (hasChildren(t)) {
        final JComponent rendererComponent = this;
        JPanel result = new JPanel(new BorderLayout()) {
          @Override
          public String getToolTipText(MouseEvent event) {
            return rendererComponent.getToolTipText(event);
          }
        };
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
  }

  /**
   * {@link FinderRecursivePanel} disposes right component on selection change if it is disposable.
   * If an inheritor creates disposable objects during right component creation,
   * the disposable objects must be registered as children of the created right component
   * in order to dispose created objects on selection change.
   * {@link DisposablePanel} could be used as a right component in that case.
   */
  protected static class DisposablePanel extends JPanel implements Disposable {
    public DisposablePanel(LayoutManager layout, @Nullable Disposable parent) {
      super(layout);
      if (parent != null) {
        Disposer.register(parent, this);
      }
    }

    @Override
    public void dispose() {
    }
  }
}