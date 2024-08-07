// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.fileChooser.impl.FileChooserUsageCollector;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
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
import java.io.File;
import java.util.List;
import java.util.*;

public class FileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, PathChooserDialog {
  public static final String FILE_CHOOSER_SHOW_PATH_PROPERTY = "FileChooser.ShowPath";

  protected final FileChooserDescriptor myChooserDescriptor;
  protected FileSystemTreeImpl myFileSystemTree;
  private Project myProject;
  private VirtualFile[] myChosenFiles = VirtualFile.EMPTY_ARRAY;

  private JPanel myNorthPanel;
  private TextFieldAction myTextFieldAction;
  protected FileTextFieldImpl myPathTextField;
  private ComboBox<String> myPath;

  private MergingUpdateQueue myUiUpdater;
  private boolean myTreeIsUpdating;

  public FileChooserDialogImpl(final @NotNull FileChooserDescriptor descriptor, @Nullable Project project) {
    super(project, true);
    myChooserDescriptor = descriptor;
    myProject = project;
    setTitle(getChooserTitle(descriptor));
  }

  public FileChooserDialogImpl(final @NotNull FileChooserDescriptor descriptor, @NotNull Component parent) {
    this(descriptor, parent, null);
  }

  public FileChooserDialogImpl(final @NotNull FileChooserDescriptor descriptor, @NotNull Component parent, @Nullable Project project) {
    super(parent, true);
    myChooserDescriptor = descriptor;
    myProject = project;
    setTitle(getChooserTitle(descriptor));
  }

  private static @NlsContexts.DialogTitle String getChooserTitle(final FileChooserDescriptor descriptor) {
    final String title = descriptor.getTitle();
    return title != null ? title : UIBundle.message("file.chooser.default.title");
  }

  @Override
  public VirtualFile @NotNull [] choose(final @Nullable Project project, final VirtualFile @NotNull ... toSelect) {
    init();
    if (myProject == null && project != null) {
      myProject = project;
    }
    if (toSelect.length == 1) {
      restoreSelection(toSelect[0]);
    }
    else if (toSelect.length == 0) {
      restoreSelection(null); // select the last opened file
    }
    else {
      selectInTree(toSelect, true, true);
    }

    show();

    FileChooserUsageCollector.log(this, myChooserDescriptor, myChosenFiles);
    return myChosenFiles;
  }

  @Override
  public void choose(@Nullable VirtualFile toSelect, @NotNull Consumer<? super List<VirtualFile>> callback) {
    init();
    restoreSelection(toSelect);

    show();

    FileChooserUsageCollector.log(this, myChooserDescriptor, myChosenFiles);
    if (myChosenFiles.length > 0) {
      callback.consume(Arrays.asList(myChosenFiles));
    }
    else if (callback instanceof FileChooser.FileChooserConsumer) {
      ((FileChooser.FileChooserConsumer)callback).cancelled();
    }
  }

  protected void restoreSelection(@Nullable VirtualFile toSelect) {
    VirtualFile file = FileChooserUtil.getFileToSelect(myChooserDescriptor, myProject, toSelect);
    if (file != null && file.isValid()) {
      myPathTextField.setText(getPresentableUrl(file), true, () ->
        selectInTree(new VirtualFile[]{file}, false, false));
    }
  }

  @ApiStatus.Internal
  void storeSelection(@Nullable VirtualFile file) {
    if (file != null) {
      FileChooserUtil.updateRecentPaths(myProject, file);
    }
  }

  protected DefaultActionGroup createActionGroup() {
    registerTreeActionShortcut("FileChooser.Delete");
    registerTreeActionShortcut("FileChooser.Refresh");

    var group = new DefaultActionGroup();
    for (var action : ((DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserToolbar")).getChildActionsOrStubs()) {
      group.addAction(action);
    }
    for (var action : ((DefaultActionGroup)ActionManager.getInstance().getAction("FileChooserSettings")).getChildActionsOrStubs()) {
      if (action instanceof ActionStub stub && "FileChooser.ShowHidden".equals(stub.getId())) {
        action.getTemplatePresentation().setIcon(AllIcons.Actions.ToggleVisibility);
        group.addAction(action);
      }
    }
    return group;
  }

  private void registerTreeActionShortcut(String actionId) {
    final JTree tree = myFileSystemTree.getTree();
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    action.registerCustomShortcutSet(action.getShortcutSet(), tree, getDisposable());
  }

  @Override
  protected final @Nullable JComponent createTitlePane() {
    final String description = myChooserDescriptor.getDescription();
    if (StringUtil.isEmptyOrSpaces(description)) return null;

    final JLabel label = new JLabel(description);
    label.setBorder(BorderFactory.createCompoundBorder(
      new SideBorder(UIUtil.getPanelBackground().darker(), SideBorder.BOTTOM),
      JBUI.Borders.empty(0, 5, 10, 5)));
    return label;
  }

  @NotNull
  protected FileLookup.Finder createFinder() {
    return new LocalFsFinder();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new MyPanel();

    myUiUpdater = new MergingUpdateQueue("FileChooserUpdater", 200, false, panel);
    Disposer.register(getDisposable(), myUiUpdater);
    UiNotifyConnector.installOn(panel, myUiUpdater);

    panel.setBorder(JBUI.Borders.empty());

    createTree();

    final DefaultActionGroup group = createActionGroup();
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar("FileChooserDialog", group, true);
    toolBar.setTargetComponent(panel);

    final JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(toolBar.getComponent(), BorderLayout.CENTER);

    myTextFieldAction = new TextFieldAction() {
      @Override
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        toggleShowTextField();
      }
    };
    toolbarPanel.add(myTextFieldAction, BorderLayout.EAST);
    JPanel extraToolbarPanel = createExtraToolbarPanel();
    if(extraToolbarPanel != null){
      toolbarPanel.add(extraToolbarPanel, BorderLayout.SOUTH);
    }

    myPath = new ComboBox<>(FileChooserUtil.getRecentPaths().toArray(ArrayUtilRt.EMPTY_STRING_ARRAY));
    myPath.setEditable(true);
    myPath.setRenderer(SimpleListCellRenderer.create((var label, @NlsContexts.Label var value, var index) -> {
      label.setText(value);
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-338208, EA-831292")) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(value));
        label.setIcon(file == null ? EmptyIcon.ICON_16 : IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, null));
      }
    }));

    JTextField pathEditor = (JTextField)myPath.getEditor().getEditorComponent();
    FileLookup.LookupFilter filter =
      f -> myChooserDescriptor.isFileVisible(((LocalFsFinder.VfsFile)f).getFile(), myFileSystemTree.areHiddensShown());
    myPathTextField = new FileTextFieldImpl(pathEditor, createFinder(), filter, FileChooserFactoryImpl.getMacroMap(), getDisposable()) {
      @Override
      protected void onTextChanged(String newValue) {
        myUiUpdater.cancelAllUpdates();
        updateTreeFromPath(newValue);
      }
    };

    myNorthPanel = new JPanel(new BorderLayout());
    myNorthPanel.add(toolbarPanel, BorderLayout.NORTH);

    updateTextFieldShowing();

    panel.add(myNorthPanel, BorderLayout.NORTH);

    registerMouseListener(group);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myFileSystemTree.getTree());
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setPreferredSize(JBUI.size(400));

    JLabel dndLabel = new JLabel(UIBundle.message("file.chooser.tooltip.drag.drop"), SwingConstants.CENTER);
    dndLabel.setFont(JBUI.Fonts.miniFont());
    dndLabel.setForeground(UIUtil.getLabelDisabledForeground());
    panel.add(dndLabel, BorderLayout.SOUTH);

    ApplicationManager.getApplication().getMessageBus().connect(getDisposable())
      .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(@NotNull IdeFrame ideFrame) {
          SaveAndSyncHandler.getInstance().maybeRefresh(ModalityState.current());
        }
      });

    return panel;
  }

  protected @Nullable JPanel createExtraToolbarPanel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (isToShowTextField()) {
      return myPathTextField != null ? myPathTextField.getField() : null;
    }
    else {
      return myFileSystemTree != null ? myFileSystemTree.getTree() : null;
    }
  }

  @Override
  public final void dispose() {
    LocalFileSystem.getInstance().removeWatchedRoots(myRequests.values());
    super.dispose();
  }

  private boolean isTextFieldActive() {
    return myPathTextField.getField().getRootPane() != null;
  }

  @Override
  protected void doOKAction() {
    if (!isOKActionEnabled()) {
      return;
    }

    if (isTextFieldActive()) {
      final String text = myPathTextField.getTextFieldText();
      final LookupFile file = myPathTextField.getFile();
      if (text == null || file == null || !file.exists()) {
        setErrorText(IdeBundle.message("dialog.message.specified.path.cannot.be.found"), myPathTextField.getField());
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

  @Override
  public final void doCancelAction() {
    myChosenFiles = VirtualFile.EMPTY_ARRAY;
    super.doCancelAction();
  }

  protected JTree createTree() {
    Tree internalTree = createInternalTree();
    myFileSystemTree = new FileSystemTreeImpl(myProject, myChooserDescriptor, internalTree, null, null, null);
    internalTree.setRootVisible(myChooserDescriptor.isTreeRootVisible());
    internalTree.setShowsRootHandles(true);
    Disposer.register(getDisposable(), myFileSystemTree);

    myFileSystemTree.addOkAction(this::doOKAction);
    JTree tree = myFileSystemTree.getTree();
    if (!Registry.is("file.chooser.async.tree.model")) tree.setCellRenderer(new NodeRenderer());
    tree.getSelectionModel().addTreeSelectionListener(new FileTreeSelectionListener());
    tree.addTreeExpansionListener(new FileTreeExpansionListener());
    setOKActionEnabled(false);

    myFileSystemTree.addListener(selection -> {
      // myTreeIsUpdating makes no sense for AsyncTreeModel
      if (myTreeIsUpdating) myTreeIsUpdating = false;
      updatePathFromTree(selection, false);
    }, getDisposable());

    new FileDrop(tree, new FileDrop.Target() {
      @Override
      public FileChooserDescriptor getDescriptor() {
        return myChooserDescriptor;
      }

      @Override
      public boolean isHiddenShown() {
        return myFileSystemTree.areHiddensShown();
      }

      @Override
      public void dropFiles(final List<? extends VirtualFile> files) {
        if (!myChooserDescriptor.isChooseMultiple() && !files.isEmpty()) {
          selectInTree(new VirtualFile[]{files.get(0)}, true, true);
        }
        else {
          selectInTree(VfsUtilCore.toVirtualFileArray(files), true, true);
        }
      }
    });

    return tree;
  }

  protected @NotNull Tree createInternalTree() {
    return new Tree();
  }

  private void registerMouseListener(final ActionGroup group) {
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
    @Override
    public void treeExpanded(TreeExpansionEvent event) {
      final Object[] path = event.getPath().getPath();
      if (path.length == 2 && path[1] instanceof DefaultMutableTreeNode) {
        // the top node has been expanded => watch disk recursively
        final Object userObject = ((DefaultMutableTreeNode)path[1]).getUserObject();
        if (userObject instanceof FileNodeDescriptor) {
          final VirtualFile file = ((FileNodeDescriptor)userObject).getElement().getFile();
          if (file != null && file.isDirectory()) {
            final String rootPath = file.getPath();
            if (myRequests.get(rootPath) == null) {
              final LocalFileSystem.WatchRequest watchRequest = LocalFileSystem.getInstance().addRootToWatch(rootPath, true);
              if (watchRequest != null) {
                myRequests.put(rootPath, watchRequest);
              }
            }
          }
        }
      }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) { }
  }

  private final class FileTreeSelectionListener implements TreeSelectionListener {
    @Override
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

  protected final class MyPanel extends JPanel implements UiDataProvider {
    final PasteProvider myPasteProvider = new PasteProvider() {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void performPaste(@NotNull DataContext dataContext) {
        if (myPathTextField != null) {
          String path = calculatePath();
          myPathTextField.setText(path, true, null);
          updateTreeFromPath(path);
        }
      }

      private static @Nullable String calculatePath() {
        final Transferable contents = CopyPasteManager.getInstance().getContents();
        if (contents != null) {
          final List<File> fileList = FileCopyPasteUtil.getFileList(contents);
          if (fileList != null) {
            if (!fileList.isEmpty()) {
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

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(FileSystemTree.DATA_KEY, myFileSystemTree);
      sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, myFileSystemTree == null ? null : myFileSystemTree.getSelectedFiles());
      sink.set(PlatformDataKeys.PASTE_PROVIDER, myPasteProvider);
      DataSink.uiDataSnapshot(sink, dataId -> myChooserDescriptor.getUserData(dataId));
    }
  }

  public void toggleShowTextField() {
    setToShowTextField(!isToShowTextField());
    updateTextFieldShowing();
  }

  @NotNull
  @NlsSafe
  protected String getPresentableUrl(@NotNull VirtualFile virtualFile) {
    return VfsUtil.getReadableUrl(virtualFile);
  }

  private void updateTextFieldShowing() {
    myTextFieldAction.update();
    myNorthPanel.remove(myPath);
    if (isToShowTextField()) {
      List<VirtualFile> selection = new ArrayList<>();
      if (myFileSystemTree.getSelectedFile() != null) {
        selection.add(myFileSystemTree.getSelectedFile());
      }
      updatePathFromTree(selection, true);
      myNorthPanel.add(myPath, BorderLayout.CENTER);
    }
    else {
      setErrorText(null);
    }
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPathTextField.getField(), true));

    myNorthPanel.revalidate();
    myNorthPanel.repaint();
  }


  private void updatePathFromTree(final List<? extends VirtualFile> selection, boolean now) {
    if (!isToShowTextField() || myTreeIsUpdating) return;

    String text = "";
    if (!selection.isEmpty()) {
      text = getPresentableUrl(selection.get(0));
    }
    else {
      final List<VirtualFile> roots = myChooserDescriptor.getRoots();
      if (!myFileSystemTree.getTree().isRootVisible() && roots.size() == 1) {
        text = getPresentableUrl(roots.get(0));
      }
    }
    if (text.isEmpty()) return;
    String old = myPathTextField.getTextFieldText();
    if (old == null || old.equals(text)) return;
    int index = old.length() - 1;
    if (index == text.length() && File.separatorChar == old.charAt(index) && old.startsWith(text)) return;

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
      @Override
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          LookupFile toFind = myPathTextField.getFile();
          if (toFind instanceof LocalFsFinder.VfsFile && toFind.exists()) {
            myUiUpdater.queue(new Update("treeFromPath.2") {
              @Override
              public void run() {
                selectInTree(((LocalFsFinder.VfsFile)toFind).getFile(), text);
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
    if (!Set.of(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
      myFileSystemTree.select(array, () -> {
        if (!myFileSystemTree.areHiddensShown() && !Set.of(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
          // try to select files in hidden folders
          myFileSystemTree.showHiddens(true);
          selectInTree(array, requestFocus, updatePathNeeded);
          return;
        }
        if (array.length == 1 && !Set.of(myFileSystemTree.getSelectedFiles()).containsAll(fileList)) {
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
