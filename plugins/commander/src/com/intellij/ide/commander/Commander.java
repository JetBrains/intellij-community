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
package com.intellij.ide.commander;

import com.intellij.diff.actions.CompareFilesAction;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.TwoPaneIdeView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.AutoScrollToSourceHandler;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
@State(name = "Commander", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class Commander extends JPanel implements PersistentStateComponent<Element>, DataProvider, TwoPaneIdeView, Disposable {
  private final Project myProject;
  private CommanderPanel myLeftPanel;
  private CommanderPanel myRightPanel;
  private final Splitter mySplitter;
  private final ListSelectionListener mySelectionListener;
  private final ListDataListener myListDataListener;
  public boolean MOVE_FOCUS = true; // internal option: move focus to editor when class/file/...etc. is created
  private Element myElement;
  private final FocusWatcher myFocusWatcher;
  private final CommanderHistory myHistory;
  private boolean myAutoScrollMode;
  private final ToolWindowManager myToolWindowManager;
  @NonNls private static final String ACTION_BACKCOMMAND = "backCommand";
  @NonNls private static final String ACTION_FORWARDCOMMAND = "forwardCommand";
  @NonNls private static final String ELEMENT_LEFTPANEL = "leftPanel";
  @NonNls private static final String ATTRIBUTE_MOVE_FOCUS = "MOVE_FOCUS";
  @NonNls private static final String ELEMENT_OPTION = "OPTION";
  @NonNls private static final String ATTRIBUTE_PROPORTION = "proportion";
  @NonNls private static final String ELEMENT_SPLITTER = "splitter";
  @NonNls private static final String ELEMENT_RIGHTPANEL = "rightPanel";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";

  /**
   * FOR USE IN TESTS ONLY!!!
   * @param project
   * @param keymapManager
   */
  public Commander(final Project project, KeymapManager keymapManager) {
    this(project, keymapManager, null);
  }

  public Commander(final Project project, KeymapManager keymapManager, final ToolWindowManager toolWindowManager) {
    super(new BorderLayout());
    myProject = project;
    myToolWindowManager = toolWindowManager;

    final AbstractAction backAction = new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        myHistory.back();
      }
    };
    final AbstractAction fwdAction = new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        myHistory.forward();
      }
    };
    final ActionMap actionMap = getActionMap();
    actionMap.put(ACTION_BACKCOMMAND, backAction);
    actionMap.put(ACTION_FORWARDCOMMAND, fwdAction);
    final KeyStroke[] backStrokes = getKeyStrokes(IdeActions.ACTION_GOTO_BACK, keymapManager);
    for (KeyStroke stroke : backStrokes) {
      //getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "backCommand");
      //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "backCommand");
      registerKeyboardAction(backAction, ACTION_BACKCOMMAND, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
      registerKeyboardAction(backAction, ACTION_BACKCOMMAND, stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    final KeyStroke[] fwdStrokes = getKeyStrokes(IdeActions.ACTION_GOTO_FORWARD, keymapManager);
    for (KeyStroke stroke : fwdStrokes) {
      //getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "forwardCommand");
      //getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, "forwardCommand");
      registerKeyboardAction(fwdAction, ACTION_FORWARDCOMMAND, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
      registerKeyboardAction(fwdAction, ACTION_FORWARDCOMMAND, stroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    myHistory = new CommanderHistory(this);

    mySelectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        updateToolWindowTitle();
      }
    };
    myListDataListener = new ListDataListener() {
      @Override
      public void intervalAdded(final ListDataEvent e) {
        updateToolWindowTitle();
      }

      @Override
      public void intervalRemoved(final ListDataEvent e) {
        updateToolWindowTitle();
      }

      @Override
      public void contentsChanged(final ListDataEvent e) {
        updateToolWindowTitle();
      }
    };
    myFocusWatcher = new FocusWatcher();

    myLeftPanel = createPanel();
    myLeftPanel.addHistoryListener(new CommanderHistoryListener() {
      @Override
      public void historyChanged(final PsiElement selectedElement, final boolean elementExpanded) {
        getCommandHistory().saveState(selectedElement, elementExpanded, true);
      }
    });
    myRightPanel = createPanel();
    myRightPanel.addHistoryListener(new CommanderHistoryListener() {
      @Override
      public void historyChanged(final PsiElement selectedElement, final boolean elementExpanded) {
        getCommandHistory().saveState(selectedElement, elementExpanded, false);
      }
    });

    mySplitter = new Splitter();
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myRightPanel);

    add(mySplitter, BorderLayout.CENTER);

    final AutoScrollToSourceHandler handler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myAutoScrollMode;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myAutoScrollMode = state;
      }
    };
    handler.install(myLeftPanel.getList());
    handler.install(myRightPanel.getList());

    final boolean shouldAddToolbar = !ApplicationManager.getApplication().isUnitTestMode();
    if (shouldAddToolbar) {
      final DefaultActionGroup toolbarActions = createToolbarActions();
      toolbarActions.add(handler.createToggleAction());
      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.COMMANDER_TOOLBAR, toolbarActions, true);
      add(toolbar.getComponent(), BorderLayout.NORTH);
    }

    myFocusWatcher.install(this);
  }

  public static Commander getInstance(final Project project) {
    return ServiceManager.getService(project, Commander.class);
  }

  public CommanderHistory getCommandHistory() {
    return myHistory;
  }

  private void processConfigurationElement() {
    if (myElement == null) return;

    Element element = myElement.getChild(ELEMENT_LEFTPANEL);
    if (element != null) {
      final PsiElement parentElement = readParentElement(element);
      if (parentElement != null) {
        myLeftPanel.getBuilder().enterElement(parentElement, PsiUtilCore.getVirtualFile(parentElement));
      }
    }

    element = myElement.getChild(ELEMENT_RIGHTPANEL);
    if (element != null) {
      final PsiElement parentElement = readParentElement(element);
      if (parentElement != null) {
        myRightPanel.getBuilder().enterElement(parentElement, PsiUtilCore.getVirtualFile(parentElement));
      }
    }

    element = myElement.getChild(ELEMENT_SPLITTER);
    if (element != null) {
      final String attribute = element.getAttributeValue(ATTRIBUTE_PROPORTION);
      if (attribute != null) {
        try {
          final float proportion = Float.valueOf(attribute).floatValue();
          if (proportion >= 0 && proportion <= 1) {
            mySplitter.setProportion(proportion);
          }
        }
        catch (NumberFormatException ignored) {
        }
      }
    }

    element = myElement.getChild(ELEMENT_OPTION);
    if (element != null) {
      //noinspection HardCodedStringLiteral
      MOVE_FOCUS = !"false".equals(element.getAttributeValue(ATTRIBUTE_MOVE_FOCUS));
    }

    myLeftPanel.setActive(false);
    myRightPanel.setActive(false);
    myLeftPanel.setMoveFocus(MOVE_FOCUS);
    myRightPanel.setMoveFocus(MOVE_FOCUS);

    myElement = null;
  }

  private static KeyStroke[] getKeyStrokes(String actionId, KeymapManager keymapManager) {
    final Shortcut[] shortcuts = keymapManager.getActiveKeymap().getShortcuts(actionId);
    final List<KeyStroke> strokes = new ArrayList<>();
    for (final Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        strokes.add(((KeyboardShortcut)shortcut).getFirstKeyStroke());
      }
    }
    return strokes.toArray(new KeyStroke[strokes.size()]);
  }

  private DefaultActionGroup createToolbarActions() {
    final ActionManager actionManager = ActionManager.getInstance();
    final DefaultActionGroup group = new DefaultActionGroup();

    final AnAction backAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myHistory.back();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myHistory.canGoBack());
      }
    };
    backAction.copyFrom(actionManager.getAction(IdeActions.ACTION_GOTO_BACK));
    group.add(backAction);

    final AnAction forwardAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myHistory.forward();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myHistory.canGoForward());
      }
    };
    forwardAction.copyFrom(actionManager.getAction(IdeActions.ACTION_GOTO_FORWARD));
    group.add(forwardAction);

    group.add(actionManager.getAction("CommanderSwapPanels"));
    group.add(actionManager.getAction("CommanderSyncViews"));

    return group;
  }

  private CommanderPanel createPanel() {
    final CommanderPanel panel = new CommanderPanel(myProject, true, false);

    panel.getList().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(final KeyEvent e) {
        if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
          if (e.isConsumed()) return;
          final PsiCopyPasteManager copyPasteManager = PsiCopyPasteManager.getInstance();
          final boolean[] isCopied = new boolean[1];
          if (copyPasteManager.getElements(isCopied) != null && !isCopied[0]) {
            copyPasteManager.clear();
            e.consume();
          }
        }
      }
    });


    final ProjectAbstractTreeStructureBase treeStructure = createProjectTreeStructure();
    panel.setBuilder(new ProjectListBuilder(myProject, panel, treeStructure, AlphaComparator.INSTANCE, true));
    panel.setProjectTreeStructure(treeStructure);

    final FocusAdapter focusListener = new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent e) {
        updateToolWindowTitle(panel);
      }
    };
    final JList list = panel.getList();
    list.addFocusListener(focusListener);
    list.getSelectionModel().addListSelectionListener(mySelectionListener);
    list.getModel().addListDataListener(myListDataListener);

    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        list.removeFocusListener(focusListener);
        list.getSelectionModel().removeListSelectionListener(mySelectionListener);
        list.getModel().removeListDataListener(myListDataListener);
      }
    });
    return panel;
  }

  protected AbstractProjectTreeStructure createProjectTreeStructure() {
    return new AbstractProjectTreeStructure(myProject) {
      @Override
      public boolean isShowMembers() {
        return true;
      }

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return false;
      }

      @Override
      public boolean isFlattenPackages() {
        return false;
      }

      @Override
      public boolean isAbbreviatePackageNames() {
        return false;
      }

      @Override
      public boolean isShowLibraryContents() {
        return false;
      }

      @Override
      public boolean isShowModules() {
        return false;
      }
    };
  }

  /**
   * invoked in AWT thread
   */
  private void updateToolWindowTitle() {
    final CommanderPanel panel = getActivePanel();
    updateToolWindowTitle(panel);
  }

  protected void updateToolWindowTitle(CommanderPanel activePanel) {
    final ToolWindow toolWindow = myToolWindowManager.getToolWindow(ToolWindowId.COMMANDER);
    if (toolWindow != null) {
      final AbstractTreeNode node = activePanel.getSelectedNode();
      if (node instanceof ProjectViewNode) {
        toolWindow.setTitle(((ProjectViewNode)node).getTitle());
      }
    }
  }

  public boolean isLeftPanelActive() {
    return isPanelActive(myLeftPanel);
  }

  boolean isPanelActive(final CommanderPanel panel) {
    return panel.getList() == myFocusWatcher.getFocusedComponent();
  }

  public void selectElementInLeftPanel(final Object element, VirtualFile virtualFile) {
    myLeftPanel.getBuilder().selectElement(element, virtualFile);
    if (!isPanelActive(myLeftPanel)) {
      switchActivePanel();
    }
  }

  public void selectElementInRightPanel(final Object element, VirtualFile virtualFile) {
    myRightPanel.getBuilder().selectElement(element, virtualFile);
    if (!isPanelActive(myRightPanel)) {
      switchActivePanel();
    }
  }

  public void switchActivePanel() {
    final CommanderPanel activePanel = getActivePanel();
    final CommanderPanel inactivePanel = getInactivePanel();
    inactivePanel.setActive(true);
    activePanel.setActive(false);
    IdeFocusTraversalPolicy.getPreferredFocusedComponent(inactivePanel).requestFocus();
  }

  public void enterElementInActivePanel(final PsiElement element) {
    final CommanderPanel activePanel = isLeftPanelActive() ? myLeftPanel : myRightPanel;
    activePanel.getBuilder().enterElement(element, PsiUtilCore.getVirtualFile(element));
  }

  public void swapPanels() {
    mySplitter.swapComponents();

    final CommanderPanel tmpPanel = myLeftPanel;
    myLeftPanel = myRightPanel;
    myRightPanel = tmpPanel;
  }

  public void syncViews() {
    final CommanderPanel activePanel;
    final CommanderPanel passivePanel;
    if (isLeftPanelActive()) {
      activePanel = myLeftPanel;
      passivePanel = myRightPanel;
    } else {
      activePanel = myRightPanel;
      passivePanel = myLeftPanel;
    }
    ProjectViewNode element = (ProjectViewNode)activePanel.getBuilder().getParentNode();
    passivePanel.getBuilder().enterElement(element);
  }

  public CommanderPanel getActivePanel() {
    return isLeftPanelActive() ? myLeftPanel : myRightPanel;
  }

  public CommanderPanel getInactivePanel() {
    return !isLeftPanelActive() ? myLeftPanel : myRightPanel;
  }

  @Override
  public Object getData(final String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HelpID.COMMANDER;
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (LangDataKeys.TARGET_PSI_ELEMENT.is(dataId)) {
      final AbstractTreeNode parentElement = getInactivePanel().getBuilder().getParentNode();
      if (parentElement == null) return null;
      final Object element = parentElement.getValue();
      return element instanceof PsiElement && ((PsiElement)element).isValid()? element : null;
    }
    else if (CompareFilesAction.DIFF_REQUEST.is(dataId)) {
      PsiElement primary = getActivePanel().getSelectedElement();
      PsiElement secondary = getInactivePanel().getSelectedElement();
      if (primary != null && secondary != null &&
          primary.isValid() && secondary.isValid() &&
          !PsiTreeUtil.isAncestor(primary, secondary, false) &&
          !PsiTreeUtil.isAncestor(secondary, primary, false)) {
        return PsiDiffContentFactory.comparePsiElements(primary, secondary);
      }
      return null;
    }
    else {
      return getActivePanel().getDataImpl(dataId);
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("commander");
    if (myLeftPanel == null || myRightPanel == null) {
      return element;
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    Element e = new Element(ELEMENT_LEFTPANEL);
    element.addContent(e);
    writePanel(myLeftPanel, e);
    e = new Element(ELEMENT_RIGHTPANEL);
    element.addContent(e);
    writePanel(myRightPanel, e);
    e = new Element(ELEMENT_SPLITTER);
    element.addContent(e);
    e.setAttribute(ATTRIBUTE_PROPORTION, Float.toString(mySplitter.getProportion()));
    if (!MOVE_FOCUS) {
      e = new Element(ELEMENT_OPTION);
      element.addContent(e);
      //noinspection HardCodedStringLiteral
      e.setAttribute(ATTRIBUTE_MOVE_FOCUS, "false");
    }
    return element;
  }

  private static void writePanel(final CommanderPanel panel, final Element element) {
    /*TODO[anton,vova]: it's a patch!!!*/
    final AbstractListBuilder builder = panel.getBuilder();
    if (builder == null) return;

    final AbstractTreeNode parentNode = builder.getParentNode();
    final Object parentElement = parentNode != null? parentNode.getValue() : null;
    if (parentElement instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory) parentElement;
      element.setAttribute(ATTRIBUTE_URL, directory.getVirtualFile().getUrl());
    }
    else if (parentElement instanceof PsiClass) {
      for (PsiElement e = (PsiElement) parentElement; e != null && e.isValid(); e = e.getParent()) {
        if (e instanceof PsiClass) {
          final String qualifiedName = ((PsiClass) e).getQualifiedName();
          if (qualifiedName != null) {
            element.setAttribute(ATTRIBUTE_CLASS, qualifiedName);
            break;
          }
        }
      }
    }
  }

  @Override
  public void loadState(Element state) {
    myElement = state;
    processConfigurationElement();
    myElement = null;
  }

  private PsiElement readParentElement(final Element element) {
    if (element.getAttributeValue(ATTRIBUTE_URL) != null) {
      final String url = element.getAttributeValue(ATTRIBUTE_URL);
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      return file != null ? PsiManager.getInstance(myProject).findDirectory(file) : null;
    }
    if (element.getAttributeValue(ATTRIBUTE_CLASS) != null) {
      final String className = element.getAttributeValue(ATTRIBUTE_CLASS);
      return className != null ? JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject)) : null;
    }
    return null;
  }

  @Override
  public void dispose() {
    if (myLeftPanel == null) {
      // not opened project (default?)
      return;
    }
    myLeftPanel.dispose();
    myRightPanel.dispose();
    myHistory.clearHistory();
  }

  public CommanderPanel getRightPanel() {
    return myRightPanel;
  }

  public CommanderPanel getLeftPanel() {
    return myLeftPanel;
  }

  @Override
  public void selectElement(PsiElement element, boolean selectInActivePanel) {
    CommanderPanel panel = selectInActivePanel ? getActivePanel() : getInactivePanel();
    panel.getBuilder().selectElement(element, PsiUtilCore.getVirtualFile(element));
  }
}

