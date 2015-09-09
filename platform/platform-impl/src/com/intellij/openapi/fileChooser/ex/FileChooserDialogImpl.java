/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, PathChooserDialog, FileLookup {
  @NonNls public static final String FILE_CHOOSER_SHOW_PATH_PROPERTY = "FileChooser.ShowPath";
  public static final String RECENT_FILES_KEY = "file.chooser.recent.files";
  public static final String DRAG_N_DROP_HINT =
    "<html><center><small><font color=gray>Drag and drop a file into the space above to quickly locate it in the tree</font></small></center></html>";
  private final FileChooserDescriptor myChooserDescriptor;
  protected FileSystemTreeImpl myFileSystemTree;
  private Project myProject;
  private VirtualFile[] myChosenFiles = VirtualFile.EMPTY_ARRAY;

  private JPanel myNorthPanel;
  private TextFieldAction myTextFieldAction;
  protected FileTextFieldImpl myPathTextField;
  private JComponent myPathTextFieldWrapper;

  private MergingUpdateQueue myUiUpdater;
  private boolean myTreeIsUpdating;

  public static DataKey<PathField> PATH_FIELD = DataKey.create("PathField");

  public FileChooserDialogImpl(@NotNull final FileChooserDescriptor descriptor, @Nullable Project project) {
    super(project, true);
    myChooserDescriptor = descriptor;
    myProject = project;
    setTitle(getChooserTitle(descriptor));
  }

  public FileChooserDialogImpl(@NotNull final FileChooserDescriptor descriptor, @NotNull Component parent) {
    this(descriptor, parent, null);
  }

  public FileChooserDialogImpl(@NotNull final FileChooserDescriptor descriptor, @NotNull Component parent, @Nullable Project project) {
    super(parent, true);
    myChooserDescriptor = descriptor;
    myProject = project;
    setTitle(getChooserTitle(descriptor));
  }

  private static String getChooserTitle(final FileChooserDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.default.title");
  }

  @Override
  @NotNull
  public VirtualFile[] choose(@Nullable final Project project, @NotNull final VirtualFile... toSelect) {
    init();
    if ((myProject == null) && (project != null)) {
      myProject = project;
    }
    if (toSelect.length == 1) {
      restoreSelection(toSelect[0]);
    }
    else if (toSelect.length == 0) {
      restoreSelection(null); // select last opened file
    }
    else {
      selectInTree(toSelect, true);
    }

    show();

    return myChosenFiles;
  }


  @NotNull
  @Override
  public VirtualFile[] choose(@Nullable final VirtualFile toSelect, @Nullable final Project project) {
    if (toSelect == null) {
      return choose(project);
    }
    return choose(project, toSelect);
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<List<VirtualFile>> callback) {
    init();
    restoreSelection(toSelect);
    show();
    if (myChosenFiles.length > 0) {
      callback.consume(Arrays.asList(myChosenFiles));
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  protected void restoreSelection(@Nullable VirtualFile toSelect) {
    final VirtualFile lastOpenedFile = FileChooserUtil.getLastOpenedFile(myProject);
    final VirtualFile file = FileChooserUtil.getFileToSelect(myChooserDescriptor, myProject, toSelect, lastOpenedFile);

    if (file != null && file.isValid()) {
      myFileSystemTree.select(file, new Runnable() {
        public void run() {
          if (!file.equals(myFileSystemTree.getSelectedFile())) {
            VirtualFile parent = file.getParent();
            if (parent != null) {
              myFileSystemTree.select(parent, null);
            }
          }
          else if (file.isDirectory()) {
            myFileSystemTree.expand(file, null);
          }
        }
      });
    }
  }

  protected void storeSelection(@Nullable VirtualFile file) {
    FileChooserUtil.setLastOpenedFile(myProject, file);
    if (file != null && file.getFileSystem() instanceof LocalFileSystem) {
      saveRecent(file.getPath());
    }
  }

  protected void saveRecent(String path) {
    final List<String> files = new ArrayList<String>(Arrays.asList(getRecentFiles()));
    files.remove(path);
    files.add(0, path);
    while (files.size() > 30) {
      files.remove(files.size() - 1);
    }
    PropertiesComponent.getInstance().setValues(RECENT_FILES_KEY, ArrayUtil.toStringArray(files));
  }

  @NotNull
  private String[] getRecentFiles() {
    final String[] recent = PropertiesComponent.getInstance().getValues(RECENT_FILES_KEY);
    if (recent != null) {
      if (recent.length > 0 && myPathTextField.getField().getText().replace('\\', '/').equals(recent[0])) {
        final String[] pathes = new String[recent.length - 1];
        System.arraycopy(recent, 1, pathes, 0, recent.length - 1);
        return pathes;
      }
      return recent;
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }


  protected JComponent createHistoryButton() {
    JLabel label = new JLabel(AllIcons.Actions.Get);
    label.setToolTipText("Recent files");
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        showRecentFilesPopup();
        return true;
      }
    }.installOn(label);

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        showRecentFilesPopup();
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(!IdeEventQueue.getInstance().isPopupActive());
      }
    }.registerCustomShortcutSet(KeyEvent.VK_DOWN, 0, myPathTextField.getField());
    return label;
  }

  private void showRecentFilesPopup() {
    final JBList files = new JBList(getRecentFiles()) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myPathTextField.getField().getWidth(), super.getPreferredSize().height);
      }
    };
    files.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        final String path = value.toString();
        append(path);
        final VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
        if (file != null) {
          setIcon(IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, null));
        }
      }
    });
    JBPopupFactory.getInstance()
      .createListPopupBuilder(files)
      .setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          myPathTextField.getField().setText(files.getSelectedValue().toString());
        }
      }).createPopup().showUnderneathOf(myPathTextField.getField());
  }


  protected DefaultActionGroup createActionGroup() {
    registerFileChooserShortcut(IdeActions.ACTION_DELETE, "FileChooser.Delete");
    registerFileChooserShortcut(IdeActions.ACTION_SYNCHRONIZE, "FileChooser.Refresh");

    return (DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserToolbar");
  }

  private void registerFileChooserShortcut(@NonNls final String baseActionId, @NonNls final String fileChooserActionId) {
    final JTree tree = myFileSystemTree.getTree();
    final AnAction syncAction = ActionManager.getInstance().getAction(fileChooserActionId);

    AnAction original = ActionManager.getInstance().getAction(baseActionId);
    syncAction.registerCustomShortcutSet(original.getShortcutSet(), tree, myDisposable);
  }

  @Nullable
  protected final JComponent createTitlePane() {
    final String description = myChooserDescriptor.getDescription();
    if (StringUtil.isEmptyOrSpaces(description)) return null;

    final JLabel label = new JLabel(description);
    label.setBorder(BorderFactory.createCompoundBorder(
      new SideBorder(UIUtil.getPanelBackground().darker(), SideBorder.BOTTOM),
      JBUI.Borders.empty(0, 5, 10, 5)));
    return label;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new MyPanel();

    myUiUpdater = new MergingUpdateQueue("FileChooserUpdater", 200, false, panel);
    Disposer.register(myDisposable, myUiUpdater);
    new UiNotifyConnector(panel, myUiUpdater);

    panel.setBorder(JBUI.Borders.empty());

    createTree();

    final DefaultActionGroup group = createActionGroup();
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    toolBar.setTargetComponent(panel);

    final JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(toolBar.getComponent(), BorderLayout.CENTER);

    myTextFieldAction = new TextFieldAction() {
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        toggleShowTextField();
      }
    };
    toolbarPanel.add(myTextFieldAction, BorderLayout.EAST);
    JPanel extraToolbarPanel = createExtraToolbarPanel();
    if(extraToolbarPanel != null){
      toolbarPanel.add(extraToolbarPanel, BorderLayout.SOUTH);
    }

    myPathTextFieldWrapper = new JPanel(new BorderLayout());
    myPathTextFieldWrapper.setBorder(JBUI.Borders.emptyBottom(2));
    myPathTextField = new FileTextFieldImpl.Vfs(
      FileChooserFactoryImpl.getMacroMap(), getDisposable(),
      new LocalFsFinder.FileChooserFilter(myChooserDescriptor, myFileSystemTree)) {
      protected void onTextChanged(final String newValue) {
        myUiUpdater.cancelAllUpdates();
        updateTreeFromPath(newValue);
      }
    };
    Disposer.register(myDisposable, myPathTextField);
    myPathTextFieldWrapper.add(myPathTextField.getField(), BorderLayout.CENTER);
    if (getRecentFiles().length > 0) {
      myPathTextFieldWrapper.add(createHistoryButton(), BorderLayout.EAST);
    }

    myNorthPanel = new JPanel(new BorderLayout());
    myNorthPanel.add(toolbarPanel, BorderLayout.NORTH);


    updateTextFieldShowing();

    panel.add(myNorthPanel, BorderLayout.NORTH);

    registerMouseListener(group);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFileSystemTree.getTree());
    //scrollPane.setBorder(BorderFactory.createLineBorder(new Color(148, 154, 156)));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setPreferredSize(JBUI.size(400));


    panel.add(new JLabel(DRAG_N_DROP_HINT, SwingConstants.CENTER), BorderLayout.SOUTH);


    ApplicationManager.getApplication().getMessageBus().connect(getDisposable())
      .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener.Adapter() {
        @Override
        public void applicationActivated(IdeFrame ideFrame) {
          DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_MODAL, new Runnable() {
            @Override
            public void run() {
              ((SaveAndSyncHandlerImpl)SaveAndSyncHandler.getInstance()).maybeRefresh(ModalityState.current());
            }
          });
        }
      });

    return panel;
  }

  @Nullable
  protected JPanel createExtraToolbarPanel() {
    return null;
  }

  public JComponent getPreferredFocusedComponent() {
    if (isToShowTextField()) {
      return myPathTextField != null ? myPathTextField.getField() : null;
    }
    else {
      return myFileSystemTree != null ? myFileSystemTree.getTree() : null;
    }
  }

  public final void dispose() {
    LocalFileSystem.getInstance().removeWatchedRoots(myRequests.values());
    super.dispose();
  }

  private boolean isTextFieldActive() {
    return myPathTextField.getField().getRootPane() != null;
  }

  protected void doOKAction() {
    if (!isOKActionEnabled()) {
      return;
    }

    if (isTextFieldActive()) {
      final String text = myPathTextField.getTextFieldText();
      final LookupFile file = myPathTextField.getFile();
      if (text == null || file == null || !file.exists()) {
        setErrorText("Specified path cannot be found");
        return;
      }
    }

    final List<VirtualFile> selectedFiles = Arrays.asList(getSelectedFilesInt());
    final VirtualFile[] files = VfsUtilCore.toVirtualFileArray(FileChooserUtil.getChosenFiles(myChooserDescriptor, selectedFiles));
    if (files.length == 0) {
      myChosenFiles = VirtualFile.EMPTY_ARRAY;
      close(CANCEL_EXIT_CODE);
      return;
    }

    try {
      myChooserDescriptor.validateSelectedFiles(files);
    }
    catch (Exception e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage(), getTitle());
      return;
    }

    myChosenFiles = files;
    storeSelection(files[files.length - 1]);

    super.doOKAction();
  }

  public final void doCancelAction() {
    myChosenFiles = VirtualFile.EMPTY_ARRAY;
    super.doCancelAction();
  }

  protected JTree createTree() {
    Tree internalTree = createInternalTree();
    myFileSystemTree = new FileSystemTreeImpl(myProject, myChooserDescriptor, internalTree, null, null, null);
    internalTree.setRootVisible(myChooserDescriptor.isTreeRootVisible());
    internalTree.setShowsRootHandles(true);
    Disposer.register(myDisposable, myFileSystemTree);

    myFileSystemTree.addOkAction(new Runnable() {
      public void run() {
        doOKAction();
      }
    });
    JTree tree = myFileSystemTree.getTree();
    tree.setCellRenderer(new NodeRenderer());
    tree.getSelectionModel().addTreeSelectionListener(new FileTreeSelectionListener());
    tree.addTreeExpansionListener(new FileTreeExpansionListener());
    setOKActionEnabled(false);

    myFileSystemTree.addListener(new FileSystemTree.Listener() {
      public void selectionChanged(final List<VirtualFile> selection) {
        updatePathFromTree(selection, false);
      }
    }, myDisposable);

    new FileDrop(tree, new FileDrop.Target() {
      public FileChooserDescriptor getDescriptor() {
        return myChooserDescriptor;
      }

      public boolean isHiddenShown() {
        return myFileSystemTree.areHiddensShown();
      }

      public void dropFiles(final List<VirtualFile> files) {
        if (!myChooserDescriptor.isChooseMultiple() && files.size() > 0) {
          selectInTree(new VirtualFile[]{files.get(0)}, true);
        }
        else {
          selectInTree(VfsUtilCore.toVirtualFileArray(files), true);
        }
      }
    });

    return tree;
  }

  @NotNull
  protected Tree createInternalTree() {
    return new Tree();
  }

  protected final void registerMouseListener(final ActionGroup group) {
    myFileSystemTree.registerMouseListener(group);
  }

  private VirtualFile[] getSelectedFilesInt() {
    if (myTreeIsUpdating || !myUiUpdater.isEmpty()) {
      if (isTextFieldActive() && !StringUtil.isEmpty(myPathTextField.getTextFieldText())) {
        LookupFile toFind = myPathTextField.getFile();
        if (toFind instanceof LocalFsFinder.VfsFile && toFind.exists()) {
          VirtualFile file = ((LocalFsFinder.VfsFile)toFind).getFile();
          if (file != null) {
            return new VirtualFile[]{file};
          }
        }
      }
      return VirtualFile.EMPTY_ARRAY;
    }

    return myFileSystemTree.getSelectedFiles();
  }

  private final Map<String, LocalFileSystem.WatchRequest> myRequests = new HashMap<String, LocalFileSystem.WatchRequest>();

  private static boolean isToShowTextField() {
    return PropertiesComponent.getInstance().getBoolean(FILE_CHOOSER_SHOW_PATH_PROPERTY, true);
  }

  private static void setToShowTextField(boolean toShowTextField) {
    PropertiesComponent.getInstance().setValue(FILE_CHOOSER_SHOW_PATH_PROPERTY, Boolean.toString(toShowTextField));
  }

  private final class FileTreeExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      final Object[] path = event.getPath().getPath();
      if (path.length == 2) {
        // top node has been expanded => watch disk recursively
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path[1];
        Object userObject = node.getUserObject();
        if (userObject instanceof FileNodeDescriptor) {
          final VirtualFile file = ((FileNodeDescriptor)userObject).getElement().getFile();
          if (file != null && file.isDirectory()) {
            final String rootPath = file.getPath();
            if (myRequests.get(rootPath) == null) {
              final LocalFileSystem.WatchRequest watchRequest = LocalFileSystem.getInstance().addRootToWatch(rootPath, true);
              myRequests.put(rootPath, watchRequest);
            }
          }
        }
      }
    }

    public void treeCollapsed(TreeExpansionEvent event) {
    }
  }

  private final class FileTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();

      boolean enabled = true;
      for (TreePath treePath : paths) {
        if (!e.isAddedPath(treePath)) {
          continue;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (!(userObject instanceof FileNodeDescriptor)) {
          enabled = false;
          break;
        }
        FileElement descriptor = ((FileNodeDescriptor)userObject).getElement();
        VirtualFile file = descriptor.getFile();
        enabled = file != null && myChooserDescriptor.isFileSelectable(file);
      }
      setOKActionEnabled(enabled);
    }
  }

  protected final class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout(0, 0));
    }

    public Object getData(String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return myFileSystemTree.getSelectedFiles();
      }
      else if (PATH_FIELD.is(dataId)) {
        return new PathField() {
          public void toggleVisible() {
            toggleShowTextField();
          }
        };
      }
      else if (FileSystemTree.DATA_KEY.is(dataId)) {
        return myFileSystemTree;
      }
      return myChooserDescriptor.getUserData(dataId);
    }
  }

  public void toggleShowTextField() {
    setToShowTextField(!isToShowTextField());
    updateTextFieldShowing();
  }

  private void updateTextFieldShowing() {
    myTextFieldAction.update();
    myNorthPanel.remove(myPathTextFieldWrapper);
    if (isToShowTextField()) {
      final ArrayList<VirtualFile> selection = new ArrayList<VirtualFile>();
      if (myFileSystemTree.getSelectedFile() != null) {
        selection.add(myFileSystemTree.getSelectedFile());
      }
      updatePathFromTree(selection, true);
      myNorthPanel.add(myPathTextFieldWrapper, BorderLayout.CENTER);
    }
    else {
      setErrorText(null);
    }
    myPathTextField.getField().requestFocus();

    myNorthPanel.revalidate();
    myNorthPanel.repaint();
  }


  private void updatePathFromTree(final List<VirtualFile> selection, boolean now) {
    if (!isToShowTextField() || myTreeIsUpdating) return;

    String text = "";
    if (selection.size() > 0) {
      text = VfsUtil.getReadableUrl(selection.get(0));
    }
    else {
      final List<VirtualFile> roots = myChooserDescriptor.getRoots();
      if (!myFileSystemTree.getTree().isRootVisible() && roots.size() == 1) {
        text = VfsUtil.getReadableUrl(roots.get(0));
      }
    }

    myPathTextField.setText(text, now, new Runnable() {
      public void run() {
        myPathTextField.getField().selectAll();
        setErrorText(null);
      }
    });
  }

  private void updateTreeFromPath(final String text) {
    if (!isToShowTextField()) return;
    if (myPathTextField.isPathUpdating()) return;
    if (text == null) return;

    myUiUpdater.queue(new Update("treeFromPath.1") {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            final LocalFsFinder.VfsFile toFind = (LocalFsFinder.VfsFile)myPathTextField.getFile();
            if (toFind == null || !toFind.exists()) return;

            myUiUpdater.queue(new Update("treeFromPath.2") {
              public void run() {
                selectInTree(toFind.getFile(), text);
              }
            });
          }
        });
      }
    });
  }

  private void selectInTree(final VirtualFile vFile, String fromText) {
    if (vFile != null && vFile.isValid()) {
      if (fromText == null || fromText.equalsIgnoreCase(myPathTextField.getTextFieldText())) {
        selectInTree(new VirtualFile[]{vFile}, false);
      }
    }
    else {
      reportFileNotFound();
    }
  }

  private void selectInTree(final VirtualFile[] array, final boolean requestFocus) {
    myTreeIsUpdating = true;
    final List<VirtualFile> fileList = Arrays.asList(array);
    if (!Arrays.asList(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
      myFileSystemTree.select(array, new Runnable() {
        public void run() {
          if (!myFileSystemTree.areHiddensShown() && !Arrays.asList(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
            myFileSystemTree.showHiddens(true);
            selectInTree(array, requestFocus);
            return;
          }

          myTreeIsUpdating = false;
          setErrorText(null);
          if (requestFocus) {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myFileSystemTree.getTree().requestFocus();
              }
            });
          }
        }
      });
    }
    else {
      myTreeIsUpdating = false;
      setErrorText(null);
    }
  }

  private void reportFileNotFound() {
    myTreeIsUpdating = false;
    setErrorText(null);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "FileChooserDialogImpl";
  }

  @Override
  protected String getHelpId() {
    return "select.path.dialog";
  }
}
