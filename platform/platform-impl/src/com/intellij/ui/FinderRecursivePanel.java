// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nls;
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

import static com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor;

/**
 * @param <T> List item type. Must implement {@code equals()/hashCode()} correctly.
 */
public abstract class FinderRecursivePanel<T> extends OnePixelSplitter implements DataProvider, UserDataHolder, Disposable {

  @NotNull
  private final Project myProject;

  @Nullable
  private final String myGroupId;

  @Nullable
  private final FinderRecursivePanel<?> myParent;

  @Nullable
  private JComponent myChild = null;

  // whether panel should call getListItems() from NonBlockingReadAction
  private boolean myNonBlockingLoad = false;

  protected JBList<T> myList;
  protected final CollectionListModel<T> myListModel = new CollectionListModel<>();

  private final MergingUpdateQueue myMergingUpdateQueue = new MergingUpdateQueue("FinderRecursivePanel", 100, true, this, this);
  private volatile boolean isMergeListItemsRunning;

  private final AtomicBoolean myUpdateSelectedPathModeActive = new AtomicBoolean();

  private final Object myUpdateCoalesceKey = new Object();

  private final CopyProvider myCopyProvider = new CopyProvider() {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      final T value = getSelectedValue();
      if (value != null) {
        CopyPasteManager.getInstance().setContents(new StringSelection(getItemText(value)));
      }
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

  protected FinderRecursivePanel(@NotNull FinderRecursivePanel<?> parent) {
    this(parent.getProject(), parent, parent.getGroupId());
  }

  protected FinderRecursivePanel(@NotNull Project project, @Nullable String groupId) {
    this(project, null, groupId);
  }

  protected FinderRecursivePanel(@NotNull Project project,
                                 @Nullable FinderRecursivePanel<?> parent,
                                 @Nullable String groupId) {
    super(false, 0f);

    myProject = project;
    myParent = parent;
    myGroupId = groupId;

    if (myParent != null) {
      Disposer.register(myParent, this);
    }
  }

  protected boolean isNonBlockingLoad() {
    return myNonBlockingLoad;
  }

  protected void setNonBlockingLoad(boolean nonBlockingLoad) {
    myNonBlockingLoad = nonBlockingLoad;
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

  @Nls
  protected String getListEmptyText() {
    return IdeBundle.message("empty.text.no.entries");
  }

  @NotNull
  @Nls
  protected abstract String getItemText(@NotNull T t);

  @Nullable
  protected Icon getItemIcon(@NotNull T t) {
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
   */
  @Nullable
  protected @NlsContexts.Tooltip String getItemTooltipText(@NotNull T t) {
    return null;
  }

  protected abstract boolean hasChildren(@NotNull T t);

  /**
   * To determine item list background color (if enabled).
   *
   * @param t Current item.
   * @return Containing file.
   */
  @Nullable
  protected VirtualFile getContainingFile(@NotNull T t) {
    return null;
  }

  protected boolean isEditable() {
    return getSelectedValue() != null;
  }

  @Nullable
  protected JComponent createRightComponent(@NotNull T t) {
    return new JPanel();
  }

  @Nullable
  protected JComponent createDefaultRightComponent() {
    return new JBPanelWithEmptyText().withEmptyText(IdeBundle.message("empty.text.nothing.selected"));
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
      list.setFixedCellHeight(JBUIScale.scale(UIUtil.LIST_FIXED_CELL_HEIGHT));
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
    AnAction previousPanelAction = new AnAction(IdeBundle.messagePointer("action.FinderRecursivePanel.text.previous"), AllIcons.Actions.Back) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!isRootPanel());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        assert myParent != null;
        myParent.handleGotoPrevious();
      }
    };
    previousPanelAction.registerCustomShortcutSet(KeyEvent.VK_LEFT, 0, list);

    AnAction nextPanelAction = new AnAction(IdeBundle.messagePointer("action.FinderRecursivePanel.text.next"), AllIcons.Actions.Forward) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        final T value = getSelectedValue();
        e.getPresentation().setEnabled(value != null &&
                                       hasChildren(value) &&
                                       getSecondComponent() instanceof FinderRecursivePanel);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        FinderRecursivePanel<?> finderRecursivePanel = (FinderRecursivePanel<?>)getSecondComponent();
        finderRecursivePanel.handleGotoNext();
      }
    };
    nextPanelAction.registerCustomShortcutSet(KeyEvent.VK_RIGHT, 0, list);

    AnAction editAction = new AnAction(IdeBundle.messagePointer("action.FinderRecursivePanel.text.edit"), AllIcons.Actions.Edit) {

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(isEditable());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
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
    PopupHandler.installPopupMenu(list, contextActionGroup, "FinderPopup");
  }

  protected AnAction[] getCustomListActions() {
    return AnAction.EMPTY_ARRAY;
  }

  private void installSpeedSearch(JBList list) {
    //noinspection unchecked
    final ListSpeedSearch search = ListSpeedSearch.installOn(list, o -> getItemText((T)o));
    search.setComparator(new SpeedSearchComparator(false));
  }

  private void installEditOnDoubleClick(JBList list) {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
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

  protected void doCustomizeCellRenderer(@NotNull SimpleColoredComponent comp, @NotNull JList list, @NotNull T value,
                                         int index, boolean selected, boolean hasFocus) {
  }

  /**
   * Whether this list contains "fixed size" elements.
   *
   * @return true.
   */
  protected boolean hasFixedSizeListElements() {
    return true;
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    Object selectedValue = getSelectedValue();
    if (selectedValue == null) return null;

    if (PlatformCoreDataKeys.MODULE.is(dataId) && selectedValue instanceof Module) {
      return selectedValue;
    }
    if (selectedValue instanceof DataProvider && (!(selectedValue instanceof ValidateableNode) || ((ValidateableNode)selectedValue).isValid())) {
      return ((DataProvider)selectedValue).getData(dataId);
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyProvider;
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      return (DataProvider)slowId -> getSlowData(slowId, selectedValue);
    }
    return null;
  }

  @Nullable
  private static Object getSlowData(@NotNull String dataId, @NotNull Object selectedValue) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId) && selectedValue instanceof PsiElement) {
      return selectedValue;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId) && selectedValue instanceof Navigatable) {
      return selectedValue;
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
   */
  protected boolean isDisposed() {
    return Disposer.isDisposed(this);
  }

  @Nullable
  public T getSelectedValue() {
    return myList.getSelectedValue();
  }

  /**
   * Performs recursive update selecting given values.
   *
   * @param pathToSelect Values to select.
   */
  public void updateSelectedPath(Object... pathToSelect) {
    if (!myUpdateSelectedPathModeActive.compareAndSet(false, true)) return;
    try {
      FinderRecursivePanel<?> panel = this;
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
          panel = (FinderRecursivePanel<?>)component;
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
  public FinderRecursivePanel<?> getParentPanel() {
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
    myMergingUpdateQueue.queue(Update.create("update", () -> {
      T oldValue = getSelectedValue();
      int oldIndex = myList.getSelectedIndex();

      if (myNonBlockingLoad) {
        scheduleUpdateNonBlocking(oldValue, oldIndex);
      }
      else {
        scheduleUpdateBlocking(oldValue, oldIndex);
      }
    }));
  }

  private void scheduleUpdateBlocking(T oldSelectedValue, int oldSelectedIndex) {
    ApplicationManager.getApplication()
      .executeOnPooledThread(() -> DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
        try {
          List<T> listItems = getListItems();

          ApplicationManager.getApplication().invokeLater(() -> {
            updateList(oldSelectedValue, oldSelectedIndex, listItems);
          });
        }
        finally {
          myList.setPaintBusy(false);
        }
      }));
  }

  private void scheduleUpdateNonBlocking(T oldSelectedValue, int oldSelectedIndex) {
    ReadAction
      .nonBlocking(this::getListItems)
      .finishOnUiThread(ModalityState.any(), listItems -> {
        try {
          updateList(oldSelectedValue, oldSelectedIndex, listItems);
        }
        finally {
          myList.setPaintBusy(false);
        }
      })
      .coalesceBy(myUpdateCoalesceKey)
      .expireWith(this)
      .inSmartMode(myProject)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void updateList(T oldValue, int oldIndex, List<? extends T> listItems) {
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
  }

  protected void mergeListItems(@NotNull CollectionListModel<T> listModel, @NotNull JList<? extends T> list, @NotNull List<? extends T> newItems) {
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
      ((FinderRecursivePanel<?>)myChild).updatePanel();
    }
  }

  private void createRightComponent(boolean withUpdatePanel) {
    if (myChild instanceof Disposable) {
      Disposer.dispose((Disposable)myChild);
    }
    T value = getSelectedValue();
    if (value != null) {
      myChild = createRightComponent(value);
      if (myChild instanceof FinderRecursivePanel<?> childPanel) {
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
    FinderRecursivePanel<?> parent = myParent;
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
    @NonNls private static final String ITEM_PROPERTY = "FINDER_RECURSIVE_PANEL_ITEM_PROPERTY";

    @Override
    public String getToolTipText(MouseEvent event) {
      String toolTipText = super.getToolTipText(event);
      if (toolTipText != null) {
        return toolTipText;
      }
      @SuppressWarnings("unchecked")
      T value = (T)getClientProperty(ITEM_PROPERTY);
      return FinderRecursivePanel.this.getItemTooltipText(value);
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      mySelected = isSelected;
      myForeground = UIUtil.getTreeForeground();
      mySelectionForeground = cellHasFocus ? list.getSelectionForeground() : UIUtil.getTreeForeground();

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
        append(IdeBundle.message("progress.text.loading"));
      }

      try {
        doCustomizeCellRenderer(this, list, t, index, isSelected, cellHasFocus);
      }
      catch (IndexNotReadyException ignored) {
        // ignore
      }

      Color bg = UIUtil.getTreeBackground(isSelected, cellHasFocus);
      if (!isSelected) {
        VirtualFile file = getContainingFile(t);
        Color bgColor = file == null ? null : getFileBackgroundColor(myProject, file);
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

        final boolean isDark = ColorUtil.isDark(UIUtil.getListSelectionBackground(true));
        childrenLabel.setIcon(isDark ? AllIcons.Icons.Ide.NextStepInverted
                                     : AllIcons.Icons.Ide.NextStep);
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