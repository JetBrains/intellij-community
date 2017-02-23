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

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
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
  private final JComponent myComponent;
  private final DeleteProvider myDeletePSIElementProvider;
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyAutoScrollFromSourceHandler myAutoScrollFromSourceHandler;

  @NonNls private final String myId;
  private final MvcToolWindowDescriptor myDescriptor;

  private final MvcProjectViewState myViewState;

  public MvcProjectViewPane(final Project project, MvcToolWindowDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myId = descriptor.getToolWindowId();

    myViewState = descriptor.getProjectViewState(project);

    class TreeUpdater implements Runnable, PsiModificationTracker.Listener {
      private volatile boolean myInQueue;

      @Override
      public void run() {
        if (getTree() != null && getTreeBuilder() != null) {
          updateFromRoot(true);
        }
        myInQueue = false;
      }

      @Override
      public void modificationCountChanged() {
        if (!myInQueue) {
          myInQueue = true;
          ApplicationManager.getApplication().invokeLater(this);
        }
      }
    }

    project.getMessageBus().connect(this).subscribe(PsiModificationTracker.TOPIC, new TreeUpdater());

    myComponent = createComponent();
    DataManager.registerDataProvider(myComponent, this);

    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myViewState.autoScrollToSource;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myViewState.autoScrollToSource = state;
      }
    };

    myAutoScrollFromSourceHandler.install();
    myAutoScrollToSourceHandler.install(getTree());
    myAutoScrollToSourceHandler.onMouseClicked(getTree());

    myCopyPasteDelegator = new CopyPasteDelegator(project, myComponent) {
      @NotNull
      @Override
      protected PsiElement[] getSelectedElements() {
        return MvcProjectViewPane.this.getSelectedPSIElements();
      }
    };
    myDeletePSIElementProvider = new DeleteHandler.DefaultDeleteProvider();
  }

  public void setup(ToolWindowEx toolWindow) {
    JPanel p = new JPanel(new BorderLayout());
    p.add(myComponent, BorderLayout.CENTER);

    ContentManager contentManager = toolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(p, null, false);
    content.setDisposer(this);
    content.setCloseable(false);

    content.setPreferredFocusableComponent(createComponent());
    contentManager.addContent(content);

    contentManager.setSelectedContent(content, true);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new HideEmptyMiddlePackagesAction());
    group.add(myAutoScrollToSourceHandler.createToggleAction());
    group.add(myAutoScrollFromSourceHandler.createToggleAction());

    toolWindow.setAdditionalGearActions(group);

    TreeExpander expander = new DefaultTreeExpander(myTree);
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    AnAction collapseAction = actionsManager.createCollapseAllAction(expander, myTree);
    collapseAction.getTemplatePresentation().setIcon(AllIcons.General.CollapseAll);

    toolWindow.setTitleActions(new AnAction[]{new ScrollFromSourceAction(), collapseAction});
  }

  @Override
  public String getTitle() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Icon getIcon() {
    return myDescriptor.getFramework().getIcon();
  }

  @Override
  @NotNull
  public String getId() {
    return myId;
  }

  @Override
  public int getWeight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInitiallyVisible() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected BaseProjectTreeBuilder createBuilder(final DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure) {
      @Override
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }
    };
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    final Project project = myProject;
    final String id = getId();
    return new ProjectTreeStructure(project, id) {

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return myViewState.hideEmptyMiddlePackages;
      }

      @Override
      protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
        return new MvcProjectNode(project, this, myDescriptor);
      }
    };
  }

  @Override
  protected ProjectViewTree createTree(final DefaultTreeModel treeModel) {
    return new ProjectViewTree(myProject, treeModel) {
      public String toString() {
        return myDescriptor.getFramework().getDisplayName() + " " + super.toString();
      }

      @Override
      public DefaultMutableTreeNode getSelectedNode() {
        return MvcProjectViewPane.this.getSelectedNode();
      }
    };
  }

  @Override
  protected AbstractTreeUpdater createTreeUpdater(final AbstractTreeBuilder treeBuilder) {
    return new AbstractTreeUpdater(treeBuilder);
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

  @Override
  public void selectElement(PsiElement element) {
    PsiFileSystemItem psiFile;
    if (element instanceof PsiFileSystemItem) {
      psiFile = (PsiFileSystemItem)element;
    }
    else {
      psiFile = element.getContainingFile();
      if (psiFile == null) {
        return;
      }
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return;
    }

    selectFile(virtualFile, false);

    boolean requestFocus = true;

    if (psiFile instanceof PsiFile) {
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

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    return getSelectedDirectories();
  }

  @Override
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

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null || !framework.hasSupport(module)) {
      return null;
    }
    List<Object> result = new ArrayList<>();

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
              if (dir != null && VfsUtilCore.isAncestor(dir, file, false)) {
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

  private void selectElementAtCaret(Editor editor, boolean requestFocus) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    selectFile(file, requestFocus);
  }

  public void selectFile(@Nullable PsiFile file, boolean requestFocus) {
    if (file == null) return;
    selectFile(file.getVirtualFile(), requestFocus);
  }

  public void selectFile(@Nullable VirtualFile file, boolean requestFocus) {
    if (file == null) return;

    final List<Object> path = getSelectPath(file);
    if (path == null) return;

    final Object value = ((AbstractTreeNode)path.get(path.size() - 1)).getValue();
    select(value, file, requestFocus);
  }

  public void scrollFromSource() {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    Editor selectedTextEditor = fileEditorManager.getSelectedTextEditor();
    if (selectedTextEditor != null) {
      selectElementAtCaret(selectedTextEditor, false);
      return;
    }
    final FileEditor[] editors = fileEditorManager.getSelectedEditors();
    for (FileEditor fileEditor : editors) {
      if (fileEditor instanceof TextEditor) {
        Editor editor = ((TextEditor)fileEditor).getEditor();
        selectElementAtCaret(editor, false);
        return;
      }
    }
    final VirtualFile[] selectedFiles = fileEditorManager.getSelectedFiles();
    if (selectedFiles.length > 0) {
      PsiFile file = PsiManager.getInstance(myProject).findFile(selectedFiles[0]);
      selectFile(file, false);
    }
  }

  private void selectElementAtCaretNotLosingFocus() {
    if (IJSwingUtilities.hasFocus(this.getComponentToFocus())) return;
    scrollFromSource();
  }

  private class ScrollFromSourceAction extends AnAction implements DumbAware {
    private ScrollFromSourceAction() {
      super("Scroll from Source", "Select the file open in the active editor", AllIcons.General.Locate);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      scrollFromSource();
    }
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    protected MyAutoScrollFromSourceHandler() {
      super(MvcProjectViewPane.this.myProject, myComponent, MvcProjectViewPane.this);
    }

    @Override
    protected boolean isAutoScrollEnabled() {
      return myViewState.autoScrollFromSource;
    }

    @Override
    protected void setAutoScrollEnabled(boolean state) {
      myViewState.autoScrollFromSource = state;
      if (state) {
        selectElementAtCaretNotLosingFocus();
      }
    }

    @Override
    protected void selectElementFromEditor(@NotNull FileEditor editor) {
     selectElementAtCaretNotLosingFocus();
    }
  }

  private class HideEmptyMiddlePackagesAction extends ToggleAction implements DumbAware {
    private HideEmptyMiddlePackagesAction() {
      super("Compact Empty Middle Packages", "Show/Compact Empty Middle Packages",
            AllIcons.ObjectBrowser.CompactEmptyPackages);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myViewState.hideEmptyMiddlePackages;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      myViewState.hideEmptyMiddlePackages = flag;
      TreeUtil.collapseAll(myTree, 1);
    }
  }

}
