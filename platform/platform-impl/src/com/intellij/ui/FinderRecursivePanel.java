package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
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
import java.util.List;

public class FinderRecursivePanel<T> extends JBSplitter implements DataProvider, Disposable {

  @NotNull private final Project myProject;
  @Nullable private final String myGroupId;

  private FinderRecursivePanel myParent = null;
  private JComponent myChild = null;

  private JBList myList;
  private final CollectionListModel<T> myListModel = new CollectionListModel<T>();

  private final MergingUpdateQueue myMergingUpdateQueue;

  private MyListSelectionListener myListener;

  private CopyProvider myCopyProvider = new CopyProvider() {
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

  public FinderRecursivePanel(@NotNull FinderRecursivePanel parent) {
    this(parent.getProject(), parent, parent.getGroupId());
  }

  public FinderRecursivePanel(@NotNull Project project, @Nullable String groupId) {
    this(project, null, groupId);
  }

  protected FinderRecursivePanel(@NotNull Project project,
                                 @Nullable FinderRecursivePanel parent,
                                 @Nullable String groupId) {
    super(false, 0f);

    myProject = project;
    myParent = parent;
    myGroupId = groupId;
    myMergingUpdateQueue = new MergingUpdateQueue("FinderRecursivePanel", 100, true, this);

    if (myParent != null) {
      Disposer.register(myParent, this);
    }
    Disposer.register(this, myMergingUpdateQueue);
  }

  public void init() {
    setFirstComponent(createLeftComponent());
    setSecondComponent(createDefaultRightComponent());

    if (getGroupId() != null) {
      setAndLoadSplitterProportionKey(getGroupId() + "[" + getIndex() + "]");
    }
    setDividerWidth(3);
    setShowDividerIcon(false);
    setShowDividerControls(true);

    updatePanel();
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

  protected String getListEmptyText() {
    return "No entries";
  }

  protected JBList createList() {
    final JBList list = new JBList(myListModel);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setEmptyText(getListEmptyText());
    list.setCellRenderer(createListCellRenderer());

    installListActions(list);
    myListener = new MyListSelectionListener();
    list.addListSelectionListener(myListener);
    ListScrollingUtil.installActions(list);

    installSpeedSearch(list);

    installEditOnDoubleClick(list);
    return list;
  }

  private void installListActions(JBList list) {
    AnAction previousPanelAction = new AnAction("Previous", null, AllIcons.Actions.Back) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(!isRootPanel());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        IdeFocusManager.getInstance(myProject).requestFocus(myParent.getList(), true);
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
        JBList jbList = finderRecursivePanel.getList();
        if (!jbList.isEmpty()) {
          if (jbList.getSelectedValue() == null) {
            jbList.setSelectedIndex(0);
            finderRecursivePanel.updateRightComponent(true);
          }
        }
        IdeFocusManager.getInstance(myProject).requestFocus(jbList, true);
      }
    };
    nextPanelAction.registerCustomShortcutSet(KeyEvent.VK_RIGHT, 0, list);

    AnAction editAction = new AnAction("Edit", null, AllIcons.Actions.Edit) {

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(getSelectedValue() != null);
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
    Navigatable data = CommonDataKeys.NAVIGATABLE.getData(DataManager.getInstance().getDataContext(getList()));
    if (data != null) {
      data.navigate(true);
    }
    return false;
  }

  protected ListCellRenderer createListCellRenderer() {
    return new ColoredListCellRenderer() {

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

        final T t = (T)value;
        setIcon(getItemIcon(t));
        append(getItemText(t));

        doCustomizeCellRenderer(this, list, t, index, isSelected, cellHasFocus);

        Color bg = isSelected ? UIUtil.getTreeSelectionBackground(cellHasFocus) : UIUtil.getTreeTextBackground();
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
      protected final void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      }
    };
  }

  protected void doCustomizeCellRenderer(SimpleColoredComponent comp, JList list, T value, int index, boolean selected, boolean hasFocus) {
  }

  protected boolean hasChildren(T t) {
    return false;
  }

  protected JBList getList() {
    return myList;
  }

  @NotNull
  protected String getItemText(T t) {
    return t.toString();
  }

  @Nullable
  protected Icon getItemIcon(T t) {
    return null;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    Object selectedValue = getSelectedValue();
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId) && selectedValue != null) {
      return myCopyProvider;
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId) && selectedValue instanceof PsiElement) {
      return selectedValue;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId) && selectedValue instanceof Navigatable) {
      return selectedValue;
    }
    if (selectedValue instanceof DataProvider) {
      return ((DataProvider)selectedValue).getData(dataId);
    }
    return null;
  }

  @Override
  public void dispose() {
    myMergingUpdateQueue.cancelAllUpdates();
  }

  @SuppressWarnings("unchecked")
  public T getSelectedValue() {
    return (T)getList().getSelectedValue();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public FinderRecursivePanel getParentPanel() {
    return myParent;
  }

  @Nullable
  public String getGroupId() {
    return myGroupId;
  }

  @Nullable
  protected JComponent createRightComponent(T t) {
    return new JPanel();
  }

  protected JComponent createDefaultRightComponent() {
    final JBLabel label = new JBLabel("Nothing selected", SwingConstants.CENTER);
    label.setFontColor(UIUtil.FontColor.BRIGHTER);
    return label;
  }

  public void updatePanel() {
    myList.setPaintBusy(true);
    myMergingUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        final T oldValue = getSelectedValue();
        final int oldIndex = getList().getSelectedIndex();

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                try {
                  SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      mergeListItems(myListModel, getListItems());

                      if (myList.getSelectedIndex() < 0) {
                        myList.setSelectedIndex(myListModel.getSize() > oldIndex ? oldIndex : 0);
                      }
                      else {
                        Object newValue = myList.getSelectedValue();
                        updateRightComponent(oldValue == null || oldValue != newValue);
                      }
                    }
                  });
                }
                finally {
                  myList.setPaintBusy(false);
                }
              }
            });
          }
        });
      }
    });
  }

  public static <T> void mergeListItems(@NotNull CollectionListModel<T> listModel, @NotNull List<T> newItems) {
    // remove items
    for (int i = listModel.getSize() - 1; i >= 0; i--) {
      if (!newItems.contains(listModel.getElementAt(i))) {
        listModel.remove(i);
      }
    }
    // add items
    List<T> items = listModel.getItems();
    for (int i = 0; i < newItems.size(); i++) {
      T newItem = newItems.get(i);
      if (!items.contains(newItem)) {
        listModel.add(i, newItem);
      }
    }
  }

  public void updateRightComponent(boolean force) {
    if (force) {
      createRightComponent();
    }
    else if (myChild instanceof FinderRecursivePanel) {
      ((FinderRecursivePanel)myChild).updatePanel();
    }
  }

  private void createRightComponent() {
    T value = getSelectedValue();
    if (value != null) {
      if (myChild instanceof Disposable) {
        Disposer.dispose((Disposable)myChild);
      }
      myChild = createRightComponent(value);
      if (myChild instanceof FinderRecursivePanel) {
        ((FinderRecursivePanel)myChild).init();
      }

      setSecondComponent(myChild);
    }
  }

  /**
   * Called in read action.
   *
   * @return Items for list.
   */
  @NotNull
  protected List<T> getListItems() {
    return new SmartList<T>();
  }

  protected int getIndex() {
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

  @Nullable
  protected JComponent getChildPanel() {
    return myChild;
  }

  private class MyListSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent event) {
      if (event.getValueIsAdjusting()) return;
      updateRightComponent(true);
    }
  }
}
