/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FileChooserDialogImpl extends DialogWrapper implements FileChooserDialog, FileLookup {
  public static final DataKey<Boolean> PREFER_LAST_OVER_TO_SELECT = DataKey.create("PREFER_LAST_OVER_TO_SELECT");

  private final FileChooserDescriptor myChooserDescriptor;
  protected FileSystemTreeImpl myFileSystemTree;

  private static VirtualFile ourLastFile;
  private Project myProject;
  private VirtualFile[] myChosenFiles = VirtualFile.EMPTY_ARRAY;

  private JPanel myNorthPanel;

  private static boolean ourToShowTextField = true;
  private TextFieldAction myTextFieldAction;

  protected FileTextFieldImpl myPathTextField;

  private JComponent myPathTextFieldWrapper;

  private MergingUpdateQueue myUiUpdater;

  private boolean myTreeIsUpdating;

  public static DataKey<FileChooserDialogImpl> KEY = DataKey.create("FileChooserDialog");

  public FileChooserDialogImpl(FileChooserDescriptor chooserDescriptor, Project project) {
    super(project, true);
    myProject = project;
    myChooserDescriptor = chooserDescriptor;
    setTitle(UIBundle.message("file.chooser.default.title"));
  }

  public FileChooserDialogImpl(FileChooserDescriptor chooserDescriptor, Component parent) {
    super(parent, true);
    myChooserDescriptor = chooserDescriptor;
    setTitle(UIBundle.message("file.chooser.default.title"));
  }

  @NotNull
  public VirtualFile[] choose(VirtualFile toSelect, Project project) {
    init();

    VirtualFile selectFile = null;

    if (toSelect == null && ourLastFile == null) {
      if (project != null && project.getBaseDir() != null) {
        selectFile = project.getBaseDir();
      }
    } else {
      selectFile = (toSelect == null) ? ourLastFile : (ourLastFile == null ? toSelect : myChooserDescriptor.getUserData(
        PREFER_LAST_OVER_TO_SELECT.getName()) == null ? toSelect : ourLastFile);
    }

    final VirtualFile file = selectFile;

    if (file != null && file.isValid()) {
      myFileSystemTree.select(file, new Runnable() {
        public void run() {
          if (!file.equals(myFileSystemTree.getSelectedFile())) {
            VirtualFile parent = file.getParent();
            if (parent != null) {
              myFileSystemTree.select(parent, null);
            }
          } else if (file.isDirectory()) myFileSystemTree.expand(file);
        }
      });
    }

    show();

    return myChosenFiles;
  }

  protected DefaultActionGroup createActionGroup() {
    registerFileChooserShortcut(IdeActions.ACTION_DELETE, "FileChooser.Delete");
    registerFileChooserShortcut(IdeActions.ACTION_SYNCHRONIZE, "FileChooser.Refresh");

    return (DefaultActionGroup) ActionManager.getInstance().getAction("FileChooserToolbar");
  }

  private void registerFileChooserShortcut(@NonNls final String baseActionId, @NonNls final String fileChooserActionId) {
    final JTree tree = myFileSystemTree.getTree();
    final AnAction syncAction = ActionManager.getInstance().getAction(fileChooserActionId);

    AnAction original = ActionManager.getInstance().getAction(baseActionId);
    syncAction.registerCustomShortcutSet(original.getShortcutSet(), tree, myDisposable);
  }

  @Nullable
  protected final JComponent createTitlePane() {
    final String title = myChooserDescriptor.getTitle();
    final String description = myChooserDescriptor.getDescription();
    if ((StringUtil.isEmptyOrSpaces(title) || title.equals(UIBundle.message("file.chooser.default.title"))) &&
        StringUtil.isEmptyOrSpaces(description)) {
      return null;
    }
    return new TitlePanel(title, description);
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new MyPanel();

    myUiUpdater = new MergingUpdateQueue("FileChooserUpdater", 200, false, panel);
    Disposer.register(myDisposable, myUiUpdater);
    new UiNotifyConnector(panel, myUiUpdater);

    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    createTree();

    final DefaultActionGroup group = createActionGroup();
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);

    final JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(toolBar.getComponent(), BorderLayout.CENTER);

    myTextFieldAction = new TextFieldAction();
    toolbarPanel.add(myTextFieldAction, BorderLayout.EAST);

    myPathTextFieldWrapper = new JPanel(new BorderLayout());
    myPathTextFieldWrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
    myPathTextField = new FileTextFieldImpl.Vfs(myChooserDescriptor, myFileSystemTree.areHiddensShown(),
        FileChooserFactoryImpl.getMacroMap(), getDisposable()) {
      protected void onTextChanged(final String newValue) {
        updateTreeFromPath(newValue);
      }
    };
    Disposer.register(myDisposable, myPathTextField);
    myPathTextFieldWrapper.add(myPathTextField.getField(), BorderLayout.CENTER);

    myNorthPanel = new JPanel(new BorderLayout());
    myNorthPanel.add(toolbarPanel, BorderLayout.NORTH);


    updateTextFieldShowing();

    panel.add(myNorthPanel, BorderLayout.NORTH);

    registerMouseListener(group);

    JBScrollPane scrollPane = new JBScrollPane(myFileSystemTree.getTree());
    //scrollPane.setBorder(BorderFactory.createLineBorder(new Color(148, 154, 156)));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(400, 400));


    panel.add(new JLabel("<html><center><small><font color=gray>Drag and drop a file into the space above to quickly locate it in the tree.</font></small></center></html>", JLabel.CENTER), BorderLayout.SOUTH);

    return panel;
  }


  public JComponent getPreferredFocusedComponent() {
    if (ourToShowTextField) {
      return myPathTextField != null ? myPathTextField.getField() : null;
    } else {
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


    final VirtualFile[] selectedFiles = getSelectedFiles();
    try {
      myChooserDescriptor.validateSelectedFiles(selectedFiles);
    }
    catch (Exception e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage(), UIBundle.message("file.chooser.default.title"));
      return;
    }

    myChosenFiles = selectedFiles;
    if (selectedFiles.length == 0) {
      close(CANCEL_EXIT_CODE);
      return;
    }
    ourLastFile = selectedFiles[selectedFiles.length - 1];

    super.doOKAction();
  }

  public final void doCancelAction() {
    myChosenFiles = VirtualFile.EMPTY_ARRAY;
    super.doCancelAction();
  }

  protected JTree createTree() {
    myFileSystemTree = new FileSystemTreeImpl(myProject, myChooserDescriptor);
    Disposer.register(myDisposable, myFileSystemTree);

    myFileSystemTree.addOkAction(new Runnable() {
      public void run() {doOKAction(); }
    });
    JTree tree = myFileSystemTree.getTree();
    tree.setCellRenderer(new NodeRenderer());
    tree.addTreeSelectionListener(new FileTreeSelectionListener());
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
          selectInTree(new VirtualFile[] {files.get(0)}, true);
        } else {
          selectInTree(VfsUtil.toVirtualFileArray(files), true);
        }
      }
    });

    return tree;
  }

  protected final void registerMouseListener(final ActionGroup group) {
    myFileSystemTree.registerMouseListener(group);
  }

  protected VirtualFile[] getSelectedFiles() {
    return myFileSystemTree.getChoosenFiles();
  }

  private final Map<String, LocalFileSystem.WatchRequest> myRequests = new HashMap<String, LocalFileSystem.WatchRequest>();

  private final class FileTreeExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      final Object[] path = event.getPath().getPath();
      if (path.length == 2) {
        // top node has been expanded => watch disk recursively
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path [1];
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
      //Do not unwatch here!!!
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
      if (PlatformDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return getSelectedFiles();
      } else if (KEY.is(dataId)) {
        return FileChooserDialogImpl.this;
      }
      else if (FileSystemTree.DATA_KEY.is(dataId)) {
        return myFileSystemTree;
      }
      return myChooserDescriptor.getUserData(dataId);
    }
  }

  public void toggleShowTextField() {
    ourToShowTextField =!ourToShowTextField;
    updateTextFieldShowing();
  }

  private void updateTextFieldShowing() {
    myTextFieldAction.update();
    myNorthPanel.remove(myPathTextFieldWrapper);
    if (ourToShowTextField) {
      final ArrayList<VirtualFile> selection = new ArrayList<VirtualFile>();
      if (myFileSystemTree.getSelectedFile() != null) {
        selection.add(myFileSystemTree.getSelectedFile());
      }
      updatePathFromTree(selection, true);
      myNorthPanel.add(myPathTextFieldWrapper, BorderLayout.CENTER);
    } else {
      setErrorText(null);
    }
    myPathTextField.getField().requestFocus();

    myNorthPanel.revalidate();
    myNorthPanel.repaint();
  }


  private class TextFieldAction extends LinkLabel implements LinkListener {
    public TextFieldAction() {
      super("", null);
      setListener(this, null);
      update();
    }

    protected void onSetActive(final boolean active) {
      final String tooltip = AnAction
        .createTooltipText(ActionsBundle.message("action.FileChooser.TogglePathShowing.text"), ActionManager.getInstance().getAction("FileChooser.TogglePathShowing"));
      setToolTipText(tooltip);
    }

    protected String getStatusBarText() {
      return ActionsBundle.message("action.FileChooser.TogglePathShowing.text");
    }

    public void update() {
      setVisible(true);
      setText(ourToShowTextField ? IdeBundle.message("file.chooser.hide.path") : IdeBundle.message("file.chooser.show.path"));
    }

    public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
      toggleShowTextField();
    }
  }

  private void updatePathFromTree(final List<VirtualFile> selection, boolean now) {
    if (!ourToShowTextField || myTreeIsUpdating) return;

    String text = "";
    if (selection.size() > 0) {
      final VirtualFile vFile = selection.get(0);
      if (vFile.isInLocalFileSystem()) {
        text = vFile.getPresentableUrl();
      } else {
        text = vFile.getUrl();
      }
    } else {
      List<VirtualFile> roots = myChooserDescriptor.getRoots();
      if (!myFileSystemTree.getTree().isRootVisible() && roots.size() == 1) {
        VirtualFile vFile = roots.get(0);
        if (vFile.isInLocalFileSystem()) {
          text = vFile.getPresentableUrl();
        } else {
          text = vFile.getUrl();
        }
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
    if (!ourToShowTextField) return;
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
        selectInTree(new VirtualFile[] {vFile}, false);
      }
    } else {
      reportFileNotFound();
    }
  }

  private void selectInTree(final VirtualFile[] vFile, final boolean requestFocus) {
    myTreeIsUpdating = true;
    if (!Arrays.asList(myFileSystemTree.getSelectedFiles()).contains(vFile)) {
      myFileSystemTree.select(vFile, new Runnable() {
        public void run() {
          myTreeIsUpdating = false;
          setErrorText(null);
          if (requestFocus) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myFileSystemTree.getTree().requestFocus();
              }
            });
          }
        }
      });
    } else {
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
}
