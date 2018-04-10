// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
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
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class FileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, PathChooserDialog, FileLookup {
  @NonNls public static final String FILE_CHOOSER_SHOW_PATH_PROPERTY = "FileChooser.ShowPath";
  public static final String RECENT_FILES_KEY = "file.chooser.recent.files";
  public static final String DRAG_N_DROP_HINT = "Drag and drop a file into the space above to quickly locate it in the tree";
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

  public static final DataKey<PathField> PATH_FIELD = DataKey.create("PathField");

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
      selectInTree(toSelect, true, true);
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
      myPathTextField.setText(VfsUtil.getReadableUrl(file), true, () ->
        selectInTree(new VirtualFile[]{file}, false, false));
    }
  }

  protected void storeSelection(@Nullable VirtualFile file) {
    FileChooserUtil.setLastOpenedFile(myProject, file);
    if (file != null && file.getFileSystem() instanceof LocalFileSystem) {
      saveRecent(file.getPath());
    }
  }

  protected void saveRecent(String path) {
    final List<String> files = new ArrayList<>(Arrays.asList(getRecentFiles()));
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
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
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
      .setItemChoosenCallback(
        () -> {
          Object value = files.getSelectedValue();
          if (value != null) myPathTextField.getField().setText(value.toString());
        }).createPopup().showUnderneathOf(myPathTextField.getField());
  }


  protected DefaultActionGroup createActionGroup() {
    registerTreeActionShortcut("FileChooser.Delete");
    registerTreeActionShortcut("FileChooser.Refresh");

    return (DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserToolbar");
  }

  private void registerTreeActionShortcut(@NonNls final String actionId) {
    final JTree tree = myFileSystemTree.getTree();
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    action.registerCustomShortcutSet(action.getShortcutSet(), tree, myDisposable);
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
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar("FileChooserDialog", group, true);
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

    JLabel dndLabel = new JLabel(DRAG_N_DROP_HINT, SwingConstants.CENTER);
    dndLabel.setFont(JBUI.Fonts.miniFont());
    dndLabel.setForeground(UIUtil.getLabelDisabledForeground());
    panel.add(dndLabel, BorderLayout.SOUTH);

    ApplicationManager.getApplication().getMessageBus().connect(getDisposable())
      .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(IdeFrame ideFrame) {
          ((SaveAndSyncHandlerImpl)SaveAndSyncHandler.getInstance()).maybeRefresh(ModalityState.current());
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
        setErrorText("Specified path cannot be found", myPathTextField.getField());
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

    myFileSystemTree.addOkAction(this::doOKAction);
    JTree tree = myFileSystemTree.getTree();
    if (!Registry.is("file.chooser.async.tree.model")) tree.setCellRenderer(new NodeRenderer());
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
          selectInTree(new VirtualFile[]{files.get(0)}, true, true);
        }
        else {
          selectInTree(VfsUtilCore.toVirtualFileArray(files), true, true);
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

  private final Map<String, LocalFileSystem.WatchRequest> myRequests = new HashMap<>();

  private static boolean isToShowTextField() {
    return PropertiesComponent.getInstance().getBoolean(FILE_CHOOSER_SHOW_PATH_PROPERTY, true);
  }

  private static void setToShowTextField(boolean toShowTextField) {
    PropertiesComponent.getInstance().setValue(FILE_CHOOSER_SHOW_PATH_PROPERTY, Boolean.toString(toShowTextField));
  }

  private final class FileTreeExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      final Object[] path = event.getPath().getPath();
      if (path.length == 2 && path[1] instanceof DefaultMutableTreeNode) {
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
        if (e.isAddedPath(treePath)) {
          VirtualFile file = FileSystemTreeImpl.getVirtualFile(treePath);
          if (file == null || !myChooserDescriptor.isFileSelectable(file)) enabled = false;
        }
      }
      setOKActionEnabled(enabled);
    }
  }

  protected final class MyPanel extends JPanel implements DataProvider {
    final PasteProvider myPasteProvider = new PasteProvider() {
      @Override
      public void performPaste(@NotNull DataContext dataContext) {
        if (myPathTextField != null) {
          String path = calculatePath();
          myPathTextField.setText(path, true, null);
          updateTreeFromPath(path);
        }
      }

      @Nullable
      private String calculatePath() {
        final Transferable contents = CopyPasteManager.getInstance().getContents();
        if (contents != null) {
          final List<File> fileList = FileCopyPasteUtil.getFileList(contents);
          if (fileList != null) {
            if (fileList.size() > 0) {
              return fileList.get(0).getAbsolutePath();
            }
          }
        }
        return null;
      }

      @Override
      public boolean isPastePossible(@NotNull DataContext dataContext) {
        return isPasteEnabled(dataContext);
      }

      @Override
      public boolean isPasteEnabled(@NotNull DataContext dataContext) {
        return FileCopyPasteUtil.isFileListFlavorAvailable() && calculatePath() != null;
      }
    };

    public MyPanel() {
      super(new BorderLayout(0, 0));
    }

    public Object getData(String dataId) {
      if (PATH_FIELD.is(dataId)) {
        return (PathField)FileChooserDialogImpl.this::toggleShowTextField;
      }

      if (FileSystemTree.DATA_KEY.is(dataId)) {
        return myFileSystemTree;
      }

      if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
        return myPasteProvider;
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
      final ArrayList<VirtualFile> selection = new ArrayList<>();
      if (myFileSystemTree.getSelectedFile() != null) {
        selection.add(myFileSystemTree.getSelectedFile());
      }
      updatePathFromTree(selection, true);
      myNorthPanel.add(myPathTextFieldWrapper, BorderLayout.CENTER);
    }
    else {
      setErrorText(null);
    }
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPathTextField.getField(), true));

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

    myPathTextField.setText(text, now, () -> {
      myPathTextField.getField().selectAll();
      setErrorText(null);
    });
  }

  private void updateTreeFromPath(final String text) {
    if (!isToShowTextField()) return;
    if (myPathTextField.isPathUpdating()) return;
    if (text == null) return;

    myUiUpdater.queue(new Update("treeFromPath.1") {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          final LocalFsFinder.VfsFile toFind = (LocalFsFinder.VfsFile)myPathTextField.getFile();
          if (toFind == null || !toFind.exists()) return;

          myUiUpdater.queue(new Update("treeFromPath.2") {
            public void run() {
              selectInTree(toFind.getFile(), text);
            }
          });
        });
      }
    });
  }

  private void selectInTree(final VirtualFile vFile, String fromText) {
    if (vFile != null && vFile.isValid()) {
      if (fromText == null || fromText.equalsIgnoreCase(myPathTextField.getTextFieldText())) {
        selectInTree(new VirtualFile[]{vFile}, false, fromText == null);
      }
    }
    else {
      reportFileNotFound();
    }
  }

  private void selectInTree(VirtualFile[] array, boolean requestFocus, boolean updatePathNeeded) {
    myTreeIsUpdating = true;
    final List<VirtualFile> fileList = Arrays.asList(array);
    if (!Arrays.asList(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
      myFileSystemTree.select(array, () -> {
        if (!myFileSystemTree.areHiddensShown() && !Arrays.asList(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
          // try to select files in hidden folders
          myFileSystemTree.showHiddens(true);
          selectInTree(array, requestFocus, updatePathNeeded);
          return;
        }
        if (array.length == 1 && !Arrays.asList(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
          // try to select a parent of a missed file
          VirtualFile parent = array[0].getParent();
          if (parent != null && parent.isValid()) {
            selectInTree(new VirtualFile[]{parent}, requestFocus, updatePathNeeded);
            return;
          }
        }

        reportFileNotFound();
        if (updatePathNeeded) {
          updatePathFromTree(fileList, true);
        }
        if (requestFocus) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> myFileSystemTree.getTree().requestFocus());
        }
      });
    }
    else {
      reportFileNotFound();
      if (updatePathNeeded) {
        updatePathFromTree(fileList, true);
      }
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
