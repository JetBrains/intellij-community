// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.commander;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class CommanderPanel extends JPanel {
  private static final Color DARK_BLUE = new Color(55, 85, 134);
  private static final Color DARK_BLUE_BRIGHTER = new Color(58, 92, 149);
  private static final Color DARK_BLUE_DARKER = new Color(38, 64, 106);

  private Project myProject;
  protected AbstractListBuilder myBuilder;
  private JPanel myTitlePanel;
  private JLabel myParentTitle;
  protected final JBList myList;
  private final MyModel myModel;

  protected final ListSpeedSearch myListSpeedSearch;
  private final IdeView myIdeView = new MyIdeView();
  private final MyDeleteElementProvider myDeleteElementProvider = new MyDeleteElementProvider();
  @NonNls
  private static final String ACTION_DRILL_DOWN = "DrillDown";
  @NonNls
  private static final String ACTION_GO_UP = "GoUp";
  private ProjectAbstractTreeStructureBase myProjectTreeStructure;
  private boolean myActive = true;
  private final List<CommanderHistoryListener> myHistoryListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myMoveFocus = false;
  private final boolean myEnableSearchHighlighting;

  public CommanderPanel(final Project project, final boolean enableSearchHighlighting) {
    super(new BorderLayout());
    myProject = project;
    myEnableSearchHighlighting = enableSearchHighlighting;
    myModel = new MyModel();
    myList = new JBList(myModel);
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    myListSpeedSearch = ListSpeedSearch.installOn(myList);
    myListSpeedSearch.setClearSearchOnNavigateNoMatch(true);

    ScrollingUtil.installActions(myList);

    myList.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (myBuilder == null) return;
        myBuilder.buildRoot();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, ClientSystemInfo.isMac() ? InputEvent.META_MASK : InputEvent.CTRL_MASK),
                                  JComponent.WHEN_FOCUSED);

    myList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTION_DRILL_DOWN);
    myList.getInputMap(WHEN_FOCUSED)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, ClientSystemInfo.isMac() ? InputEvent.META_MASK : InputEvent.CTRL_MASK),
           ACTION_DRILL_DOWN);
    myList.getActionMap().put(ACTION_DRILL_DOWN, new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        drillDown();
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        drillDown();
        return true;
      }
    }.installOn(myList);

    myList.getInputMap(WHEN_FOCUSED)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, ClientSystemInfo.isMac() ? InputEvent.META_MASK : InputEvent.CTRL_MASK), ACTION_GO_UP);
    myList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), ACTION_GO_UP);
    myList.getActionMap().put(ACTION_GO_UP, new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        goUp();
      }
    });

    myList.getActionMap().put("selectAll", new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
      }
    });


    myList.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent e) {
        setActive(true);
      }

      @Override
      public void focusLost(final FocusEvent e) {
        setActive(false);
      }
    });
  }

  public boolean isEnableSearchHighlighting() {
    return myEnableSearchHighlighting;
  }

  public void addHistoryListener(@NotNull CommanderHistoryListener listener) {
    myHistoryListeners.add(listener);
  }

  private void removeHistoryListener(CommanderHistoryListener listener) {
    myHistoryListeners.remove(listener);
  }

  private void updateHistory(boolean elementExpanded) {
    PsiElement element = getNodeElement(getSelectedNode());
    for (CommanderHistoryListener listener : myHistoryListeners) {
      listener.historyChanged(element, elementExpanded);
    }
  }

  public final JList getList() {
    return myList;
  }

  public final AbstractListBuilder.Model getModel() {
    return myModel;
  }

  public void setMoveFocus(final boolean moveFocus) {
    myMoveFocus = moveFocus;
  }

  public void goUp() {
    if (myBuilder == null) {
      return;
    }
    updateHistory(true);
    myBuilder.goUp();
    updateHistory(false);
  }

  public void drillDown() {
    if (topElementIsSelected()) {
      goUp();
      return;
    }

    AbstractTreeNode<?> node = getSelectedNode();
    if (node == null) {
      return;
    }

    if (node.getChildren().isEmpty()) {
      if (!shouldDrillDownOnEmptyElement(node)) {
        navigateSelectedElement();
        return;
      }
    }

    if (myBuilder == null) {
      return;
    }
    updateHistory(false);
    myBuilder.drillDown();
    updateHistory(true);
  }

  public boolean navigateSelectedElement() {
    final AbstractTreeNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      if (selectedNode.canNavigateToSource()) {
        selectedNode.navigate(true);
        return true;
      }
    }
    return false;
  }

  protected boolean shouldDrillDownOnEmptyElement(AbstractTreeNode<?> node) {
    return node instanceof ProjectViewNode && ((ProjectViewNode<?>)node).shouldDrillDownOnEmptyElement();
  }

  private boolean topElementIsSelected() {
    int[] selectedIndices = myList.getSelectedIndices();
    return selectedIndices.length == 1 && selectedIndices[0] == 0 && myModel.getElementAt(selectedIndices[0]) instanceof TopLevelNode;
  }

  public final void setBuilder(final AbstractListBuilder builder) {
    myBuilder = builder;
    removeAll();

    myTitlePanel = new JPanel(new BorderLayout());
    myTitlePanel.setBackground(UIUtil.getControlColor());
    myTitlePanel.setOpaque(true);

    myParentTitle = new MyTitleLabel(myTitlePanel);
    myParentTitle.setText(" ");
    myParentTitle.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
    myParentTitle.setForeground(JBColor.foreground());
    myParentTitle.setUI(new RightAlignedLabelUI());
    final JPanel panel1 = new JPanel(new BorderLayout());
    panel1.setOpaque(false);
    panel1.add(Box.createHorizontalStrut(10), BorderLayout.WEST);
    panel1.add(myParentTitle, BorderLayout.CENTER);
    myTitlePanel.add(panel1, BorderLayout.CENTER);

    add(myTitlePanel, BorderLayout.NORTH);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    scrollPane.setBorder(null);
    scrollPane.getVerticalScrollBar().setFocusable(false); // otherwise the scrollbar steals focus and panel switching with tab is broken
    scrollPane.getHorizontalScrollBar().setFocusable(false);
    add(scrollPane, BorderLayout.CENTER);

    myBuilder.setParentTitle(myParentTitle);

    // TODO[vova,anton] it seems that the code below performs double focus request. Is it OK?
    myTitlePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
      }

      @Override
      public void mousePressed(final MouseEvent e) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
      }
    });
  }

  public final AbstractListBuilder getBuilder() {
    return myBuilder;
  }

  public @Nullable AbstractTreeNode<?> getSelectedNode() {
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();
    if (indices.length != 1) return null;
    int index = indices[0];
    if (index >= myModel.getSize()) return null;
    Object elementAtIndex = myModel.getElementAt(index);
    return elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode<?>)elementAtIndex : null;
  }

  private @NotNull List<AbstractTreeNode<?>> getSelectedNodes() {
    if (myBuilder == null) return Collections.emptyList();
    final int[] indices = myList.getSelectedIndices();
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();
    for (int index : indices) {
      if (index >= myModel.getSize()) continue;
      Object elementAtIndex = myModel.getElementAt(index);
      AbstractTreeNode<?> node = elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode<?>)elementAtIndex : null;
      if (node != null) {
        result.add(node);
      }
    }
    return result;
  }

  private static @Nullable Object getNodeValue(@Nullable AbstractTreeNode<?> node) {
    if (node == null) return null;
    Object value = node.getValue();
    if (value instanceof StructureViewTreeElement) {
      return ((StructureViewTreeElement)value).getValue();
    }
    return value;
  }

  public static @Nullable PsiElement getNodeElement(@Nullable AbstractTreeNode<?> node) {
    Object value = getNodeValue(node);
    return value instanceof PsiElement ? (PsiElement)value : null;
  }

  public final void setActive(final boolean active) {
    myActive = active;
    if (active) {
      myTitlePanel.setBackground(DARK_BLUE);
      myTitlePanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, DARK_BLUE_BRIGHTER, DARK_BLUE_DARKER));
      myParentTitle.setForeground(Color.white);
    }
    else {
      final Color color = UIUtil.getPanelBackground();
      myTitlePanel.setBackground(color);
      myTitlePanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, color.brighter(), color.darker()));
      myParentTitle.setForeground(JBColor.foreground());
    }
    final int[] selectedIndices = myList.getSelectedIndices();
    if (selectedIndices.length == 0 && myList.getModel().getSize() > 0) {
      myList.setSelectedIndex(0);
      if (!myList.hasFocus()) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
      }
    }
    else if (myList.getModel().getSize() > 0) {
      // need this to generate SelectionChanged events so that listeners, added by Commander, will be notified
      myList.setSelectedIndices(selectedIndices);
    }
  }

  public boolean isActive() {
    return myActive;
  }

  public final void dispose() {
    if (myBuilder != null) {
      Disposer.dispose(myBuilder);
      myBuilder = null;
    }
    myProject = null;
  }

  public final void setTitlePanelVisible(final boolean flag) {
    myTitlePanel.setVisible(flag);
  }

  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (myBuilder == null) return;
    List<AbstractTreeNode<?>> selection = getSelectedNodes();
    AbstractTreeNode<?> node = getSelectedNode();
    AbstractTreeNode<?> parentNode = myBuilder.getParentNode();

    sink.set(LangDataKeys.IDE_VIEW, myIdeView);
    sink.set(PlatformCoreDataKeys.SELECTED_ITEM, node);
    sink.set(PlatformCoreDataKeys.SELECTED_ITEMS,
             selection.isEmpty() ? ArrayUtil.EMPTY_OBJECT_ARRAY : getSelectedNodes().toArray());
    sink.set(CommonDataKeys.NAVIGATABLE_ARRAY,
             selection.isEmpty() ? Navigatable.EMPTY_NAVIGATABLE_ARRAY : selection.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY));
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteElementProvider);

    if (myProjectTreeStructure != null) {
      List<TreeStructureProvider> providers = myProjectTreeStructure.getProviders();
      if (providers != null && !providers.isEmpty()) {
        for (TreeStructureProvider provider : ContainerUtil.reverse(providers)) {
          provider.uiDataSnapshot(sink, selection);
        }
      }
    }
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      return getNodeElement(ContainerUtil.getOnlyItem(selection));
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      if (selection.isEmpty()) return PsiElement.EMPTY_ARRAY;
      return PsiUtilCore.toPsiElementArray(ContainerUtil.mapNotNull(selection, CommanderPanel::getNodeElement));
    });
    sink.lazy(LangDataKeys.PASTE_TARGET_PSI_ELEMENT, () -> {
      Object element = parentNode != null ? parentNode.getValue() : null;
      return element instanceof PsiElement o ? o : null;
    });
    sink.lazy(PlatformCoreDataKeys.MODULE, () -> {
      Object selectedValue = getNodeValue(ContainerUtil.getOnlyItem(selection));
      return selectedValue instanceof Module o ? o : null;
    });
    sink.lazy(ModuleGroup.ARRAY_DATA_KEY, () -> {
      Object selectedValue = getNodeValue(ContainerUtil.getOnlyItem(selection));
      return selectedValue instanceof ModuleGroup o ? new ModuleGroup[]{o} : null;
    });
    sink.lazy(LibraryGroupElement.ARRAY_DATA_KEY, () -> {
      Object selectedValue = getNodeValue(ContainerUtil.getOnlyItem(selection));
      return selectedValue instanceof LibraryGroupElement o ? new LibraryGroupElement[]{o} : null;
    });
    sink.lazy(NamedLibraryElement.ARRAY_DATA_KEY, () -> {
      Object selectedValue = getNodeValue(ContainerUtil.getOnlyItem(selection));
      return selectedValue instanceof NamedLibraryElement o ? new NamedLibraryElement[]{o} : null;
    });
  }

  public void setProjectTreeStructure(final ProjectAbstractTreeStructureBase projectTreeStructure) {
    myProjectTreeStructure = projectTreeStructure;
  }

  private static final class MyTitleLabel extends JLabel {
    private final JPanel myPanel;

    MyTitleLabel(final JPanel panel) {
      myPanel = panel;
    }

    @Override
    public void setText(@NlsContexts.Label String text) {
      if (text == null || text.isEmpty()) {
        text = " ";
      }
      super.setText(text);
      if (myPanel != null) {
        myPanel.setToolTipText(text.trim().isEmpty() ? null : text);
      }
    }
  }

  private final class MyDeleteElementProvider implements DeleteProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      PsiElement[] elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (elements == null || elements.length == 0) return;
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    @Override
    public boolean canDeleteElement(@NotNull final DataContext dataContext) {
      PsiElement[] elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (elements == null || elements.length == 0) return false;
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }

  private final class MyIdeView implements IdeView {
    @Override
    public void selectElement(@NotNull PsiElement element) {
      final boolean isDirectory = element instanceof PsiDirectory;
      if (!isDirectory) {
        EditorHelper.openInEditor(element);
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        myBuilder.selectElement(element, PsiUtilCore.getVirtualFile(element));
        if (!isDirectory) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (myMoveFocus) {
              ToolWindowManager.getInstance(myProject).activateEditorComponent();
            }
          });
        }
      }, ModalityState.nonModal());
    }

    private PsiDirectory getDirectory() {
      if (myBuilder == null) return null;
      final Object parentElement = myBuilder.getParentNode();
      if (parentElement instanceof AbstractTreeNode parentNode) {
        if (!(parentNode.getValue() instanceof PsiDirectory)) return null;
        return (PsiDirectory)parentNode.getValue();
      }
      else {
        return null;
      }
    }

    @Override
    public PsiDirectory @NotNull [] getDirectories() {
      PsiDirectory directory = getDirectory();
      return directory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[]{directory};
    }

    @Override
    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }

  public static final class MyModel extends AbstractListModel implements AbstractListBuilder.Model {
    final List myElements = new ArrayList();

    @Override
    public void removeAllElements() {
      int index1 = myElements.size() - 1;
      myElements.clear();
      if (index1 >= 0) {
        fireIntervalRemoved(this, 0, index1);
      }
    }

    @Override
    public void addElement(final Object obj) {
      int index = myElements.size();
      myElements.add(obj);
      fireIntervalAdded(this, index, index);
    }

    @Override
    public void replaceElements(final List newElements) {
      removeAllElements();
      myElements.addAll(newElements);
      fireIntervalAdded(this, 0, newElements.size());
    }

    @Override
    public Object[] toArray() {
      return ArrayUtil.toObjectArray(myElements);
    }

    @Override
    public int indexOf(final Object o) {
      return myElements.indexOf(o);
    }

    @Override
    public int getSize() {
      return myElements.size();
    }

    @Override
    public Object getElementAt(final int index) {
      return myElements.get(index);
    }
  }
}