/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileDrop;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * @author lesya
 */
public abstract class SelectLocationStep extends WizardStep {
  protected final FileSystemTree myFileSystemTree;
  private final FileTextFieldImpl myPathTextField;
  private final JPanel myNorthPanel = new JPanel(new BorderLayout());
  private final JComponent myPathTextFieldWrapper;

  private final TextFieldAction myTextFieldAction;
  private ActionToolbar myFileSystemToolBar;
  private VirtualFile mySelectedFile;
  private boolean myShowPath = CvsApplicationLevelConfiguration.getInstance().SHOW_PATH;
  private MergingUpdateQueue myUiUpdater;

  private final TreeSelectionListener myTreeSelectionListener = new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          updatePathFromTree(myFileSystemTree.getSelectedFile(), false);
          getWizard().updateStep();
        }
      };
  private final FileChooserDescriptor myChooserDescriptor;
  private boolean myTreeIsUpdating = false;

  public SelectLocationStep(String description, CvsWizard wizard, @Nullable final Project project, boolean showFiles) {
    super(description, wizard);
    myChooserDescriptor = new FileChooserDescriptor(showFiles, true, false, false, false, true);
    myFileSystemTree = FileSystemTreeFactory.SERVICE.getInstance().createFileSystemTree(project, myChooserDescriptor);
    myFileSystemTree.updateTree();

    myPathTextField = new FileTextFieldImpl.Vfs(
      FileChooserFactoryImpl.getMacroMap(), myFileSystemTree,
      new LocalFsFinder.FileChooserFilter(myChooserDescriptor, myFileSystemTree)) {
      protected void onTextChanged(final String newValue) {
        updateTreeFromPath(newValue);
      }
    };
    myPathTextFieldWrapper = new JPanel(new BorderLayout());
    myPathTextFieldWrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
    myPathTextFieldWrapper.add(myPathTextField.getField(), BorderLayout.CENTER);
    myTextFieldAction = new TextFieldAction();
  }

  protected void init() {
    final DefaultActionGroup fileSystemActionGroup = createFileSystemActionGroup();
    myFileSystemToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, fileSystemActionGroup, true);

    final JTree tree = myFileSystemTree.getTree();
    tree.getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);
    tree.setCellRenderer(new NodeRenderer());
    tree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        final ActionPopupMenu popupMenu =
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP, fileSystemActionGroup);
        popupMenu.getComponent().show(comp, x, y);
      }
    });
    tree.addSelectionPath(tree.getPathForRow(0));
    new FileDrop(tree, new FileDrop.Target() {
      public FileChooserDescriptor getDescriptor() {
        return myChooserDescriptor;
      }

      public boolean isHiddenShown() {
        return myFileSystemTree.areHiddensShown();
      }

      public void dropFiles(final List<VirtualFile> files) {
        if (files.size() > 0) {
          selectInTree(files.toArray(new VirtualFile[files.size()]));
        }
      }
    });
    super.init();
  }

  protected JComponent createComponent() {
    final JPanel panel = new MyPanel();
    final JPanel toolbarPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    toolbarPanel.add(myFileSystemToolBar.getComponent(), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.EAST;
    toolbarPanel.add(myTextFieldAction, constraints);
    myNorthPanel.add(toolbarPanel, BorderLayout.NORTH);
    panel.add(myNorthPanel, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myFileSystemTree.getTree()), BorderLayout.CENTER);
    panel.add(new JLabel(FileChooserDialogImpl.DRAG_N_DROP_HINT, SwingConstants.CENTER), BorderLayout.SOUTH);
    myUiUpdater = new MergingUpdateQueue("FileChooserUpdater", 200, false, panel);
    Disposer.register(myFileSystemTree, myUiUpdater);
    new UiNotifyConnector(panel, myUiUpdater);
    updateTextFieldShowing();
    return panel;
  }

  protected void dispose() {
    mySelectedFile = myFileSystemTree.getSelectedFile();   // remember the file - it will be requested after dispose
    myFileSystemTree.getTree().getSelectionModel().removeTreeSelectionListener(myTreeSelectionListener);
    Disposer.dispose(myFileSystemTree);
  }

  public boolean nextIsEnabled() {
    final VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
    return selectedFiles.length == 1 && selectedFiles[0].isDirectory();
  }

  private DefaultActionGroup createFileSystemActionGroup() {
    final DefaultActionGroup group = FileSystemTreeFactory.SERVICE.getInstance().createDefaultFileSystemActions(myFileSystemTree);
    final AnAction[] actions = getActions();
    if (actions.length > 0) group.addSeparator();

    for (AnAction action : actions) {
      group.add(action);
    }
    return group;
  }

  protected AnAction[] getActions(){
    return AnAction.EMPTY_ARRAY;
  }

  public File getSelectedFile() {
    if (mySelectedFile != null) {
      return CvsVfsUtil.getFileFor(mySelectedFile);
    }
    return CvsVfsUtil.getFileFor(myFileSystemTree.getSelectedFile());
  }

  public JComponent getPreferredFocusedComponent() {
    return myFileSystemTree.getTree();
  }

  private void updateTreeFromPath(final String text) {
    if (!myShowPath) return;
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
    myTreeIsUpdating = true;
    if (vFile == null || !vFile.isValid()) {
      return;
    }
    if (fromText == null || fromText.equalsIgnoreCase(myPathTextField.getTextFieldText())) {
      selectInTree(new VirtualFile[]{vFile});
    }
    myTreeIsUpdating = false;
  }

  private void selectInTree(VirtualFile[] vFiles) {
    if (vFiles.length == 0) return;
    myFileSystemTree.select(vFiles, null);
  }

  public void toggleShowTextField() {
    myShowPath = !myShowPath;
    CvsApplicationLevelConfiguration.getInstance().SHOW_PATH = myShowPath;
    updateTextFieldShowing();
  }

  private void updateTextFieldShowing() {
    myTextFieldAction.update();
    myNorthPanel.remove(myPathTextFieldWrapper);
    if (myShowPath) {
      updatePathFromTree(myFileSystemTree.getSelectedFile(), true);
      myNorthPanel.add(myPathTextFieldWrapper, BorderLayout.SOUTH);
    }
    myPathTextField.getField().requestFocus();

    myNorthPanel.revalidate();
    myNorthPanel.repaint();
  }

  private void updatePathFromTree(VirtualFile selection, boolean now) {
    if (!myShowPath || myTreeIsUpdating) return;

    String text = "";
    if (selection != null) {
      if (selection.isInLocalFileSystem()) {
        text = selection.getPresentableUrl();
      }
      else {
        text = selection.getUrl();
      }
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
      }
    });
  }


  private class MyPanel extends JPanel implements TypeSafeDataProvider {
    private MyPanel() {
      super(new BorderLayout());
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
        sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, myFileSystemTree.getSelectedFiles());
      }
      else if (key == FileSystemTree.DATA_KEY) {
        sink.put(FileSystemTree.DATA_KEY, myFileSystemTree);
      }
    }
  }

  private class TextFieldAction extends LinkLabel implements LinkListener {
    public TextFieldAction() {
      super("", null);
      setListener(this, null);
      update();
    }

    protected void onSetActive(final boolean active) {
      final String tooltip = KeymapUtil.createTooltipText(ActionsBundle.message("action.FileChooser.TogglePathShowing.text"),
                                                          ActionManager.getInstance().getAction("FileChooser.TogglePathShowing"));
      setToolTipText(tooltip);
    }

    protected String getStatusBarText() {
      return ActionsBundle.message("action.FileChooser.TogglePathShowing.text");
    }

    public void update() {
      setVisible(true);
      setText(myShowPath ? IdeBundle.message("file.chooser.hide.path") : IdeBundle.message("file.chooser.show.path"));
    }

    public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
      toggleShowTextField();
    }
  }
}
