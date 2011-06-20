/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ProjectTopics;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.psi.*;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.ide.*;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Krasislchikov
 */
public class MvcProjectViewPane extends AbstractProjectViewPSIPane implements IdeView {
  private final CopyPasteDelegator myCopyPasteDelegator;
  private final JPanel myComponent;
  private final DeleteProvider myDeletePSIElementProvider;
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyAutoScrollFromSourceHandler myAutoScrollFromSourceHandler;
  private boolean myAutoScrollFromSource;
  private boolean myAutoScrollToSource;
  private boolean myHideEmptyMiddlePackages = true;

  @NonNls private final String myId;
  private final MvcToolWindowDescriptor myDescriptor;

  public MvcProjectViewPane(final Project project, MvcToolWindowDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myId = descriptor.getToolWindowId();

    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myAutoScrollToSource;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myAutoScrollToSource = state;
      }
    };

    project.getMessageBus().connect(this).subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      public void modificationCountChanged() {
        if (getTree() != null && getTreeBuilder() != null) {
          updateFromRoot(true);
        }
      }
    });

    myComponent = new JPanel(new BorderLayout());
    myComponent.add(createComponent(), BorderLayout.CENTER);
    myComponent.add(createToolbar(), BorderLayout.NORTH);
    DataManager.registerDataProvider(myComponent, this);

    myCopyPasteDelegator = new CopyPasteDelegator(project, myComponent) {
      @NotNull
      @Override
      protected PsiElement[] getSelectedElements() {
        return MvcProjectViewPane.this.getSelectedPSIElements();
      }
    };
    myDeletePSIElementProvider = new DeleteHandler.DefaultDeleteProvider();
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();

    final TreeExpander expander = new DefaultTreeExpander(myTree);
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    group.addAction(new ScrollFromSourceAction());
    group.addAction(myAutoScrollFromSourceHandler.createToggleAction());
    group.addAction(myAutoScrollToSourceHandler.createToggleAction());
    group.add(actionsManager.createCollapseAllAction(expander, myTree));
    group.addAction(new HideEmptyMiddlePackagesAction());

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
  }

  @Override
  public JComponent createComponent() {
    JComponent component = super.createComponent();
    myAutoScrollFromSourceHandler.install();
    myAutoScrollToSourceHandler.install(getTree());
    myAutoScrollToSourceHandler.onMouseClicked(getTree());
    return component;
  }

  public JPanel getComponent() {
    return myComponent;
  }

  public String getTitle() {
    throw new UnsupportedOperationException();
  }

  public Icon getIcon() {
    return myDescriptor.getFramework().getIcon();
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public int getWeight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInitiallyVisible() {
    throw new UnsupportedOperationException();
  }

  public SelectInTarget createSelectInTarget() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected BaseProjectTreeBuilder createBuilder(final DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure) {
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }
    };
  }

  protected ProjectAbstractTreeStructureBase createStructure() {
    final Project project = myProject;
    final String id = getId();
    return new ProjectTreeStructure(project, id) {

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return myHideEmptyMiddlePackages;
      }

      protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
        return new MvcProjectNode(project, this, myDescriptor);
      }
    };
  }

  protected ProjectViewTree createTree(final DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      public String toString() {
        return myDescriptor.getFramework().getDisplayName() + " " + super.toString();
      }

      public DefaultMutableTreeNode getSelectedNode() {
        return MvcProjectViewPane.this.getSelectedNode();
      }
    };
  }

  protected AbstractTreeUpdater createTreeUpdater(final AbstractTreeBuilder treeBuilder) {
    return new AbstractTreeUpdater(treeBuilder);
  }

  @Nullable
  protected PsiElement getPSIElement(@Nullable final Object element) {
    // E.g is used by Project View's DataProvider
   if (element instanceof NodeId) {
      final PsiElement psiElement = ((NodeId)element).getPsiElement();
      if (psiElement != null && psiElement.isValid()) {
        return psiElement;
      }
    }
    return super.getPSIElement(element);
  }

  @Override
  public Object getData(String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      final PsiElement[] elements = getSelectedPSIElements();
      return elements.length == 1 ? elements[0] : null;
    }
    if (DataConstants.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return getSelectedPSIElements();
    }
    if (DataConstants.MODULE_CONTEXT.equals(dataId)) {
      final Object element = getSelectedElement();
      if (element instanceof Module) {
        return element;
      }
      return null;
    }
    if (DataConstants.MODULE_CONTEXT_ARRAY.equals(dataId)) {
      final List<Module> moduleList = ContainerUtil.findAll(getSelectedElements(), Module.class);
      if (!moduleList.isEmpty()) {
        return moduleList.toArray(new Module[moduleList.size()]);
      }
      return null;
    }
    if (dataId.equals(DataConstants.IDE_VIEW)) {
      return this;
    }
    if (dataId.equals(DataConstants.HELP_ID)) {
      return "reference.toolwindows." + myId.toLowerCase();
    }
    if (DataConstants.CUT_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (DataConstants.COPY_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (DataConstants.PASTE_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (DataConstants.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      for (final Object element : getSelectedElements()) {
        if (element instanceof Module) {
          return myDeleteModuleProvider;
        }
      }
      return myDeletePSIElementProvider;
    }
    return super.getData(dataId);
  }

  @Nullable
  public static MvcProjectViewPane getView(final Project project, MvcFramework framework) {
    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(MvcToolWindowDescriptor.getToolWindowId(framework));
    final Content content = window == null ? null : window.getContentManager().getContent(0);
    return content == null ? null : (MvcProjectViewPane)content.getDisposer();
  }

  public void selectElement(PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return;

    VirtualFile virtualFile = ((PsiFileSystemItem)element).getVirtualFile();

    selectFile(virtualFile, false);

    boolean requestFocus = true;

    if (element instanceof PsiFile) {
      Editor editor = EditorHelper.openInEditor(element);
      if (editor != null) {
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
        requestFocus = false;
      }
    }

    if (requestFocus) {
      selectFile(virtualFile, true);
    }
  }

  public PsiDirectory[] getDirectories() {
    return getSelectedDirectories();
  }

  public PsiDirectory getOrChooseDirectory() {
    return DirectoryChooserUtil.getOrChooseDirectory(this);
  }

  public static boolean canSelectFile(@NotNull Project project, @NotNull MvcFramework framework, VirtualFile file) {
    return getSelectPath(project, framework, file) != null;
  }

  @Nullable
  private List<Object> getSelectPath(VirtualFile file) {
    return getSelectPath(myProject, myDescriptor.getFramework(), file);
  }

  @Nullable
  private static List<Object> getSelectPath(@NotNull Project project, @NotNull MvcFramework framework, VirtualFile file) {
      if (file == null) {
      return null;
    }

    final Module module = ModuleUtil.findModuleForFile(file, project);
    if (module == null || !framework.hasSupport(module)) {
      return null;
    }
    List<Object> result = new ArrayList<Object>();

    final MvcProjectViewPane view = getView(project, framework);
    if (view == null) {
      return null;
    }

    final MvcProjectNode root = (MvcProjectNode)view.getTreeBuilder().getTreeStructure().getRootElement();
    result.add(root);

    for (AbstractTreeNode moduleNode : root.getChildren()) {
      if (moduleNode.getValue() == module) {
        result.add(moduleNode);

        AbstractTreeNode<?> cur = moduleNode;

        path:
        while (true) {
          for (AbstractTreeNode descriptor : cur.getChildren()) {
            if (descriptor instanceof AbstractFolderNode) {
              final AbstractFolderNode folderNode = (AbstractFolderNode)descriptor;
              final VirtualFile dir = folderNode.getVirtualFile();
              if (dir != null && VfsUtil.isAncestor(dir, file, false)) {
                cur = folderNode;
                result.add(folderNode);
                if (dir.equals(file)) {
                  return result;
                }
                continue path;
              }
            }
            if (descriptor instanceof AbstractMvcPsiNodeDescriptor) {
              if (file.equals(((AbstractMvcPsiNodeDescriptor)descriptor).getVirtualFile())) {
                result.add(descriptor);
                return result;
              }
            }
          }
          return null;
        }
      }
    }
    return null;
  }

  public boolean canSelectFile(VirtualFile file) {
    return getSelectPath(file) != null;
  }

  public void selectFile(VirtualFile file, boolean requestFocus) {
    final List<Object> path = getSelectPath(file);
    if (path == null) return;

    final Object value = ((AbstractTreeNode)path.get(path.size() - 1)).getValue();
    select(value, file, requestFocus);
  }

  public void scrollFromSource() {
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    //final FileEditor[] editors = fileEditorManager.getSelectedEditors();
    //for (FileEditor fileEditor : editors) {
    //  if (fileEditor instanceof TextEditor) {
    //    Editor editor = ((TextEditor)fileEditor).getEditor();
    //    selectElement();
    //    selectElementAtCaret(editor);
    //    return;
    //  }
    //}
    final VirtualFile[] selectedFiles = fileEditorManager.getSelectedFiles();
    if (selectedFiles.length > 0) {
      selectFile(selectedFiles[0], false);
    }
  }

  private void selectElementAtCaretNotLosingFocus() {
    if (IJSwingUtilities.hasFocus(this.getComponentToFocus())) return;
    scrollFromSource();
  }


  private class ScrollFromSourceAction extends AnAction implements DumbAware {
    private ScrollFromSourceAction() {
      super("Scroll from Source", "Select the file open in the active editor", IconLoader.getIcon("/general/autoscrollFromSource.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      scrollFromSource();
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    private final Alarm myAlarm = new Alarm(myProject);

    protected MyAutoScrollFromSourceHandler() {
      super(MvcProjectViewPane.this.myProject, MvcProjectViewPane.this);
    }

    @Override
    protected boolean isAutoScrollMode() {
      return myAutoScrollFromSource;
    }

    @Override
    protected void setAutoScrollMode(boolean state) {
      myAutoScrollFromSource = state;
      if (state) {
        selectElementAtCaretNotLosingFocus();
      }
    }

    @Override
    public void install() {
      FileEditorManagerAdapter myEditorManagerListener = new FileEditorManagerAdapter() {
        public void selectionChanged(final FileEditorManagerEvent event) {
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(new Runnable() {
            public void run() {
              if (myProject.isDisposed() || !getComponent().isShowing()) return;
              if (myAutoScrollFromSource) {
                selectElementAtCaretNotLosingFocus();
              }
            }
          }, 300, ModalityState.NON_MODAL);
        }
      };
      FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myEditorManagerListener, this);
    }

    @Override
    public void dispose() {
    }
  }

  private class HideEmptyMiddlePackagesAction extends ToggleAction implements DumbAware {
    private HideEmptyMiddlePackagesAction() {
      super("Compact Empty Middle Packages", "Show/Compact Empty Middle Packages",
            IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myHideEmptyMiddlePackages;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      myHideEmptyMiddlePackages = flag;
      TreeUtil.collapseAll(myTree, 1);
    }
  }

}
