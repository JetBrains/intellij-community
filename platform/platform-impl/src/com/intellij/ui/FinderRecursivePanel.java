// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.containers.ContainerUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor;

/**
 * @param <T> List item type. Must implement {@code equals()/hashCode()} correctly.
 */
public abstract class FinderRecursivePanel<T> extends OnePixelSplitter
  implements UiCompatibleDataProvider, UserDataHolder, Disposable {

  private static final Logger LOG = Logger.getInstance(FinderRecursivePanel.class);

  private final @NotNull Project myProject;

  private final @Nullable String myGroupId;

  private final @Nullable FinderRecursivePanel<?> myParent;

  private @Nullable JComponent myChild = null;

  // whether panel should call getListItems() from NonBlockingReadAction
  private boolean myNonBlockingLoad = false;

  protected JBList<T> myList;
  protected final CollectionListModel<T> myListModel = new CollectionListModel<>();
  private List<ListItemPresentation> myPresentations = Collections.emptyList();

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
  private volatile boolean myDisposed;

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
  protected abstract @NotNull List<T> getListItems();

  protected @Nls String getListEmptyText() {
    return IdeBundle.message("empty.text.no.entries");
  }

  protected abstract @NotNull @Nls String getItemText(@NotNull T t);

  protected @Nullable Icon getItemIcon(@NotNull T t) {
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
  protected @Nullable @NlsContexts.Tooltip String getItemTooltipText(@NotNull T t) {
    return null;
  }

  protected abstract boolean hasChildren(@NotNull T t);

  /**
   * To determine item list background color (if enabled).
   *
   * @param t Current item.
   * @return Containing file.
   */
  protected @Nullable VirtualFile getContainingFile(@NotNull T t) {
    return null;
  }

  protected boolean isEditable() {
    return getSelectedValue() != null;
  }

  protected @Nullable JComponent createRightComponent(@NotNull T t) {
    return new JPanel();
  }

  protected @Nullable JComponent createDefaultRightComponent() {
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

  private void installListActions(JBList<T> list) {
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

  private void installSpeedSearch(JBList<T> list) {
    final ListSpeedSearch<T> search = ListSpeedSearch.installOn(list, o -> getItemText(o));
    search.setComparator(new SpeedSearchComparator(false));
  }

  private void installEditOnDoubleClick(JBList<T> list) {
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

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    Object selectedValue = getSelectedValue();
    if (selectedValue == null) return;

    sink.set(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
    if (selectedValue instanceof Module o) {
      sink.set(PlatformCoreDataKeys.MODULE, o);
    }
    if (!(selectedValue instanceof ValidateableNode o) || o.isValid()) {
      DataSink.uiDataSnapshot(sink, selectedValue);
    }
    if (selectedValue instanceof PsiElement o) {
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> o);
    }
    if (selectedValue instanceof Navigatable o) {
      sink.lazy(CommonDataKeys.NAVIGATABLE, () -> o);
    }
  }

  @Override
  public @Nullable <U> U getUserData(@NotNull Key<U> key) {
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
    myDisposed = true;
  }

  /**
   * @return true if already disposed.
   */
  protected boolean isDisposed() {
    return myDisposed;
  }

  public @Nullable T getSelectedValue() {
    return myList.getSelectedValue();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @Nullable FinderRecursivePanel<?> getParentPanel() {
    return myParent;
  }

  protected @Nullable String getGroupId() {
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
          List<ListItemPresentation> presentations = ContainerUtil.map(listItems, this::getPresentation);
          ApplicationManager.getApplication().invokeLater(() -> {
            updateList(oldSelectedValue, oldSelectedIndex, listItems, presentations);
          });
        }
        finally {
          myList.setPaintBusy(false);
        }
      }));
  }

  private void scheduleUpdateNonBlocking(T oldSelectedValue, int oldSelectedIndex) {
    ReadAction
      .nonBlocking(() -> {
        List<T> listItems = getListItems();
        List<ListItemPresentation> presentations = ContainerUtil.map(listItems, this::getPresentation);
        return Pair.create(listItems, presentations);
      })
      .finishOnUiThread(ModalityState.any(), items -> {
        try {
          updateList(oldSelectedValue, oldSelectedIndex, items.first, items.second);
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

  private ListItemPresentation getPresentation(T item) {
    VirtualFile file = getContainingFile(item);
    Color bgColor = file == null ? null : getFileBackgroundColor(myProject, file);
    return new ListItemPresentation(getItemText(item), getItemIcon(item), bgColor);
  }

  private void updateList(T oldValue, int oldIndex, List<? extends T> listItems, List<ListItemPresentation> presentations) {
    myPresentations = presentations;
    mergeListItems(myListModel, myList, listItems);

    if (myList.isEmpty()) {
      createRightComponent();
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
      else if (newItems.isEmpty()) {
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
      createRightComponent();
    }
    else if (myChild instanceof FinderRecursivePanel) {
      ((FinderRecursivePanel<?>)myChild).updatePanel();
    }
  }

  private void createRightComponent() {
    if (myChild instanceof Disposable) {
      Disposer.dispose((Disposable)myChild);
    }
    T value = getSelectedValue();
    if (value != null) {
      myChild = createRightComponent(value);
      if (myChild instanceof FinderRecursivePanel<?> childPanel) {
        childPanel.initPanel();
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

  private final class MyListCellRenderer extends ColoredListCellRenderer<T> {
    private static final @NonNls String ITEM_PROPERTY = "FINDER_RECURSIVE_PANEL_ITEM_PROPERTY";

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
    public Component getListCellRendererComponent(JList<? extends T> list,
                                                  T value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      mySelected = isSelected;
      myForeground = UIUtil.getTreeForeground();
      mySelectionForeground = cellHasFocus ? list.getSelectionForeground() : UIUtil.getTreeForeground();

      clear();
      setFont(UIUtil.getListFont());

      putClientProperty(ITEM_PROPERTY, value);
      ListItemPresentation itemPresentation = null;
      try {
        itemPresentation = myPresentations.get(index);
      }
      catch (IndexOutOfBoundsException e) {
        LOG.error("No presentation for list item " + value, e);
      }

      if (itemPresentation != null) {
        setIcon(itemPresentation.icon);
      }
      String itemText = itemPresentation != null ? itemPresentation.text : IdeBundle.message("progress.text.loading");
      append(itemText);

      try {
        doCustomizeCellRenderer(this, list, value, index, isSelected, cellHasFocus);
      }
      catch (IndexNotReadyException ignored) {
        // ignore
      }

      Color bg = UIUtil.getTreeBackground(isSelected, cellHasFocus);
      if (!isSelected) {
        Color bgColor = itemPresentation == null ? null : itemPresentation.backgroundColor;
        bg = bgColor == null ? bg : bgColor;
      }
      setBackground(bg);

      if (hasChildren(value)) {
        final JComponent rendererComponent = this;
        JPanel result = new JPanel(new BorderLayout()) {
          @Override
          public String getToolTipText(MouseEvent event) {
            return rendererComponent.getToolTipText(event);
          }
        };
        result.getAccessibleContext().setAccessibleName(itemText);
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
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
    }
  }

  /**
   * {@link FinderRecursivePanel} disposes right component on selection change if it is disposable.
   * If an inheritor creates disposable objects during right component creation,
   * the disposable objects must be registered as children of the created right component
   * in order to dispose created objects on selection change.
   * {@link DisposablePanel} could be used as a right component in that case.
   */
  protected static final class DisposablePanel extends JPanel implements Disposable {
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

  private record ListItemPresentation(@NotNull @Nls String text,
                                      @Nullable Icon icon,
                                      @Nullable Color backgroundColor) {
  }
}