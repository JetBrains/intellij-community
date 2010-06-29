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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 15:11:11
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ShelvedChangesViewManager implements ProjectComponent {
  private final ChangesViewContentManager myContentManager;
  private final ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  private final Tree myTree = new ShelfTree();
  private Content myContent = null;
  private final ShelvedChangeDeleteProvider myDeleteProvider = new ShelvedChangeDeleteProvider();
  private boolean myUpdatePending = false;
  private Runnable myPostUpdateRunnable = null;

  public static DataKey<ShelvedChangeList[]> SHELVED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedChangeListData");
  public static DataKey<ShelvedChangeList[]> SHELVED_RECYCLED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedRecycledChangeListData");
  public static DataKey<List<ShelvedChange>> SHELVED_CHANGE_KEY = DataKey.create("ShelveChangesManager.ShelvedChange");
  public static DataKey<List<ShelvedBinaryFile>> SHELVED_BINARY_FILE_KEY = DataKey.create("ShelveChangesManager.ShelvedBinaryFile");
  private static final Object ROOT_NODE_VALUE = new Object();
  private DefaultMutableTreeNode myRoot;
  private final Map<Pair<String, String>, String> myMoveRenameInfo;

  public static ShelvedChangesViewManager getInstance(Project project) {
    return project.getComponent(ShelvedChangesViewManager.class);
  }

  public ShelvedChangesViewManager(Project project, ChangesViewContentManager contentManager, ShelveChangesManager shelveChangesManager,
                                   final MessageBus bus) {
    myProject = project;
    myContentManager = contentManager;
    myShelveChangesManager = shelveChangesManager;
    bus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myUpdatePending = true;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            updateChangesContent();
          }
        }, ModalityState.NON_MODAL);
      }
    });
    myMoveRenameInfo = new HashMap<Pair<String, String>, String>();

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer(project, myMoveRenameInfo));
    new TreeLinkMouseListener(new ShelfTreeCellRenderer(project, myMoveRenameInfo)).install(myTree);

    final AnAction showDiffAction = ActionManager.getInstance().getAction("ShelvedChanges.Diff");
    showDiffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);

    PopupHandler.installPopupHandler(myTree, "ShelvedChangesPopupMenu", ActionPlaces.UNKNOWN);

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() != 2) return;

        DiffShelvedChangesAction.showShelvedChangesDiff(DataManager.getInstance().getDataContext(myTree));
      }
    });
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(TreePath o) {
        final Object lc = o.getLastPathComponent();
        final Object lastComponent = lc == null ? null : ((DefaultMutableTreeNode) lc).getUserObject();
        if (lastComponent instanceof ShelvedChangeList) {
          return ((ShelvedChangeList) lastComponent).DESCRIPTION;
        } else if (lastComponent instanceof ShelvedChange) {
          final ShelvedChange shelvedChange = (ShelvedChange)lastComponent;
          return shelvedChange.getBeforeFileName() == null ? shelvedChange.getAfterFileName() : shelvedChange.getBeforeFileName();
        } else if (lastComponent instanceof ShelvedBinaryFile) {
          final ShelvedBinaryFile sbf = (ShelvedBinaryFile) lastComponent;
          final String value = sbf.BEFORE_PATH == null ? sbf.AFTER_PATH : sbf.BEFORE_PATH;
          int idx = value.lastIndexOf("/");
          idx = (idx == -1) ? value.lastIndexOf("\\") : idx;
          return idx > 0 ? value.substring(idx + 1) : value;
        }
        return null;
      }
    }, true);
  }

  public void projectOpened() {
    updateChangesContent();
  }

  public void projectClosed() {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ShelvedChangesViewManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void updateChangesContent() {
    myUpdatePending = false;
    final List<ShelvedChangeList> changeLists = new ArrayList<ShelvedChangeList>(myShelveChangesManager.getShelvedChangeLists());
    changeLists.addAll(myShelveChangesManager.getRecycledShelvedChangeLists());
    if (changeLists.size() == 0) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
        myContentManager.selectContent("Local");
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
        scrollPane.setBorder(null);
        myContent = ContentFactory.SERVICE.getInstance().createContent(scrollPane, VcsBundle.message("shelf.tab"), false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
      TreeState state = TreeState.createOn(myTree);
      myTree.setModel(buildChangesModel());
      state.applyTo(myTree);
      if (myPostUpdateRunnable != null) {
        myPostUpdateRunnable.run();
      }      
    }
    myPostUpdateRunnable = null;
  }

  private TreeModel buildChangesModel() {
    myRoot = new DefaultMutableTreeNode(ROOT_NODE_VALUE);   // not null for TreeState matching to work
    DefaultTreeModel model = new DefaultTreeModel(myRoot);
    final List<ShelvedChangeList> changeLists = new ArrayList<ShelvedChangeList>(myShelveChangesManager.getShelvedChangeLists());
    if (myShelveChangesManager.isShowRecycled()) {
      changeLists.addAll(myShelveChangesManager.getRecycledShelvedChangeLists());
    }
    myMoveRenameInfo.clear();

    for(ShelvedChangeList changeList: changeLists) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(changeList);
      model.insertNodeInto(node, myRoot, myRoot.getChildCount());

      final List<Object> shelvedFilesNodes = new ArrayList<Object>();
      List<ShelvedChange> changes = changeList.getChanges();
      for(ShelvedChange change: changes) {
        putMovedMessage(change.getBeforePath(), change.getAfterPath());
        shelvedFilesNodes.add(change);
      }
      List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
      for(ShelvedBinaryFile file: binaryFiles) {
        putMovedMessage(file.BEFORE_PATH, file.AFTER_PATH);
        shelvedFilesNodes.add(file);
      }
      Collections.sort(shelvedFilesNodes, ShelvedFilePatchComparator.getInstance());
      for (int i = 0; i < shelvedFilesNodes.size(); i++) {
        final Object filesNode = shelvedFilesNodes.get(i);
        final DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(filesNode);
        model.insertNodeInto(pathNode, node, i);
      }
    }
    return model;
  }

  private void putMovedMessage(final String beforeName, final String afterName) {
    final String movedMessage = RelativePathCalculator.getMovedString(beforeName, afterName);
    if (movedMessage != null) {
      myMoveRenameInfo.put(new Pair<String, String>(beforeName, afterName), movedMessage);
    }
  }

  public void activateView(final ShelvedChangeList list) {
    Runnable runnable = new Runnable() {
      public void run() {
        if (list != null) {
          TreeUtil.selectNode(myTree, TreeUtil.findNodeWithObject(myRoot, list));
        }
        myContentManager.setSelectedContent(myContent);
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
        if (!window.isVisible()) {
          window.activate(null);
        }
      }
    };
    if (myUpdatePending) {
      myPostUpdateRunnable = runnable;
    }
    else {
      runnable.run();
    }
  }

  private class ShelfTree extends Tree implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        final Set<ShelvedChangeList> changeLists = getSelectedLists(false);

        if (changeLists.size() > 0) {
          sink.put(SHELVED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_RECYCLED_CHANGELIST_KEY) {
        final Set<ShelvedChangeList> changeLists = getSelectedLists(true);

        if (changeLists.size() > 0) {
          sink.put(SHELVED_RECYCLED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_CHANGE_KEY) {
        sink.put(SHELVED_CHANGE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
      }
      else if (key == SHELVED_BINARY_FILE_KEY) {
        sink.put(SHELVED_BINARY_FILE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class));
      }
      else if (key == VcsDataKeys.CHANGES) {
        List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        if (shelvedChanges.size() > 0) {
          Change[] changes = new Change[shelvedChanges.size()];
          for(int i=0; i<shelvedChanges.size(); i++) {
            changes [i] = shelvedChanges.get(i).getChange(myProject);
          }
          sink.put(VcsDataKeys.CHANGES, changes);
        }
        else {
          final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
          if (changeLists.size() > 0) {
            List<Change> changes = new ArrayList<Change>();
            for(ShelvedChangeList changeList: changeLists) {
              shelvedChanges = changeList.getChanges();
              for(ShelvedChange shelvedChange: shelvedChanges) {
                changes.add(shelvedChange.getChange(myProject));
              }
            }
            sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
          }
        }
      }
      else if (key == PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
        sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
      }
    }

    private Set<ShelvedChangeList> getSelectedLists(final boolean recycled) {
      final TreePath[] selections = getSelectionPaths();
      final Set<ShelvedChangeList> changeLists = new HashSet<ShelvedChangeList>();
      if (selections != null) {
        for(TreePath path: selections) {
          if (path.getPathCount() >= 2) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(1);
            if (node.getUserObject() instanceof ShelvedChangeList) {
              final ShelvedChangeList list = (ShelvedChangeList)node.getUserObject();
              if (((! recycled) && (! list.isRecycled())) ||
                (recycled && list.isRecycled())) {
                changeLists.add(list);
              }
            }
          }
        }
      }
      return changeLists;
    }
  }

  private final static class ShelvedFilePatchComparator implements Comparator<Object> {
    private final static ShelvedFilePatchComparator ourInstance = new ShelvedFilePatchComparator();

    public static ShelvedFilePatchComparator getInstance() {
      return ourInstance;
    }

    public int compare(final Object o1, final Object o2) {
      final String path1 = getPath(o1);
      final String path2 = getPath(o2);
      // case-insensitive; as in local changes
      if (path1 == null) return -1;
      if (path2 == null) return 1;
      return path1.compareToIgnoreCase(path2);
    }

    private static String getPath(final Object patch) {
      String path = null;
      if (patch instanceof ShelvedBinaryFile) {
        final ShelvedBinaryFile binaryFile = (ShelvedBinaryFile) patch;
        path = binaryFile.BEFORE_PATH;
        path = (path == null) ? binaryFile.AFTER_PATH : path;
      } else if (patch instanceof ShelvedChange) {
        final ShelvedChange shelvedChange = (ShelvedChange)patch;
        path = shelvedChange.getBeforePath().replace('/', File.separatorChar);
      }
      if (path == null) {
        return null;
      }
      final int pos = path.lastIndexOf(File.separatorChar);
      return (pos >= 0) ? path.substring(pos + 1) : path;
    }
  }

  private static class ShelfTreeCellRenderer extends ColoredTreeCellRenderer {
    private final IssueLinkRenderer myIssueLinkRenderer;
    private final Map<Pair<String, String>, String> myMoveRenameInfo;

    public ShelfTreeCellRenderer(Project project, final Map<Pair<String, String>, String> moveRenameInfo) {
      myMoveRenameInfo = moveRenameInfo;
      myIssueLinkRenderer = new IssueLinkRenderer(project, this);
    }

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelvedChangeList) {
        ShelvedChangeList changeListData = (ShelvedChangeList) nodeValue;
        if (changeListData.isRecycled()) {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        } else {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION);
        }
        final int count = node.getChildCount();
        final String numFilesText = " (" + count + ((count == 1) ? " file) " : " files) ");
        append(numFilesText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        
        final String date = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(changeListData.DATE);
        append(" (" + date + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(StdFileTypes.PATCH.getIcon());
      }
      else if (nodeValue instanceof ShelvedChange) {
        ShelvedChange change = (ShelvedChange) nodeValue;
        final String movedMessage = myMoveRenameInfo.get(new Pair<String, String>(change.getBeforePath(), change.getAfterPath()));
        renderFileName(change.getBeforePath(), change.getFileStatus(), movedMessage);
      }
      else if (nodeValue instanceof ShelvedBinaryFile) {
        ShelvedBinaryFile binaryFile = (ShelvedBinaryFile) nodeValue;
        String path = binaryFile.BEFORE_PATH;
        if (path == null) {
          path = binaryFile.AFTER_PATH;
        }
        final String movedMessage = myMoveRenameInfo.get(new Pair<String, String>(binaryFile.BEFORE_PATH, binaryFile.AFTER_PATH));
        renderFileName(path, binaryFile.getFileStatus(), movedMessage);
      }
    }

    private void renderFileName(String path, final FileStatus fileStatus, final String movedMessage) {
      path = path.replace('/', File.separatorChar);
      int pos = path.lastIndexOf(File.separatorChar);
      String fileName;
      String directory;
      if (pos >= 0) {
        directory = path.substring(0, pos).replace(File.separatorChar, File.separatorChar);
        fileName = path.substring(pos+1);
      }
      else {
        directory = "<project root>";
        fileName = path;
      }
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor()));
      if (movedMessage != null) {
        append(movedMessage, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      append(" ("+ directory + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon());
    }
  }

  private class MyChangeListDeleteProvider implements DeleteProvider {
    public void deleteElement(DataContext dataContext) {
      //noinspection unchecked
      final List<ShelvedChangeList> shelvedChangeLists = getLists(dataContext);
      if (shelvedChangeLists.isEmpty()) return;
      String message = (shelvedChangeLists.size() == 1)
        ? VcsBundle.message("shelve.changes.delete.confirm", shelvedChangeLists.get(0).DESCRIPTION)
        : VcsBundle.message("shelve.changes.delete.multiple.confirm", shelvedChangeLists.size());
      int rc = Messages.showOkCancelDialog(myProject, message, VcsBundle.message("shelvedChanges.delete.title"), Messages.getWarningIcon());
      if (rc != 0) return;
      for(ShelvedChangeList changeList: shelvedChangeLists) {
        ShelveChangesManager.getInstance(myProject).deleteChangeList(changeList);
      }
    }

    public boolean canDeleteElement(DataContext dataContext) {
      //noinspection unchecked
      return ! getLists(dataContext).isEmpty();
    }

    private List<ShelvedChangeList> getLists(final DataContext dataContext) {
      final ShelvedChangeList[] shelved = SHELVED_CHANGELIST_KEY.getData(dataContext);
      final ShelvedChangeList[] recycled = SHELVED_RECYCLED_CHANGELIST_KEY.getData(dataContext);

      final List<ShelvedChangeList> shelvedChangeLists = (shelved == null && recycled == null) ?
                                                         Collections.<ShelvedChangeList>emptyList() : new ArrayList<ShelvedChangeList>();
      if (shelved != null) {
        shelvedChangeLists.addAll(Arrays.asList(shelved));
      }
      if (recycled != null) {
        shelvedChangeLists.addAll(Arrays.asList(recycled));
      }
      return shelvedChangeLists;
    }
  }

  private class MyChangesDeleteProvider implements DeleteProvider {
    public void deleteElement(DataContext dataContext) {
      final ShelvedChangeList[] shelved = SHELVED_CHANGELIST_KEY.getData(dataContext);
      if (shelved == null || (shelved.length != 1)) return;
      final List<ShelvedChange> changes = SHELVED_CHANGE_KEY.getData(dataContext);
      final List<ShelvedBinaryFile> binaryFiles = SHELVED_BINARY_FILE_KEY.getData(dataContext);

      final ShelvedChangeList list = shelved[0];

      final String message = VcsBundle.message("shelve.changes.delete.files.from.list", (changes == null ? 0 : changes.size()) +
                                                                                        (binaryFiles == null ? 0 : binaryFiles.size()));
      int rc = Messages.showOkCancelDialog(myProject, message, VcsBundle.message("shelve.changes.delete.files.from.list.title"), Messages.getWarningIcon());
      if (rc != 0) return;

      final ArrayList<ShelvedBinaryFile> oldBinaries = new ArrayList<ShelvedBinaryFile>(list.getBinaryFiles());
      final ArrayList<ShelvedChange> oldChanges = new ArrayList<ShelvedChange>(list.getChanges());

      oldBinaries.removeAll(binaryFiles);
      oldChanges.removeAll(changes);

      final List<FilePatch> patches = new ArrayList<FilePatch>();
      final List<VcsException> exceptions = new ArrayList<VcsException>();
      for (ShelvedChange change : oldChanges) {
        try {
          patches.add(change.loadFilePatch());
        }
        catch (IOException e) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException(e));
        }
        catch (PatchSyntaxException e) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException(e));
        }
      }

      myShelveChangesManager.saveRemainingPatches(list, patches, oldBinaries);

      if (! exceptions.isEmpty()) {
        String title = list.DESCRIPTION == null ? "" : list.DESCRIPTION;
        title = title.substring(0, Math.min(10, list.DESCRIPTION.length()));
        AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, "Deleting files from '" + title + "'");
      }
    }

    public boolean canDeleteElement(DataContext dataContext) {
      final ShelvedChangeList[] shelved = SHELVED_CHANGELIST_KEY.getData(dataContext);
      if (shelved == null || (shelved.length != 1)) return false;
      final List<ShelvedChange> changes = SHELVED_CHANGE_KEY.getData(dataContext);
      if (changes != null && (! changes.isEmpty())) return true;
      final List<ShelvedBinaryFile> binaryFiles = SHELVED_BINARY_FILE_KEY.getData(dataContext);
      return (binaryFiles != null && (! binaryFiles.isEmpty()));
    }
  }

  private class ShelvedChangeDeleteProvider implements DeleteProvider {
    private final List<DeleteProvider> myProviders;

    private ShelvedChangeDeleteProvider() {
      myProviders = Arrays.asList(new MyChangesDeleteProvider(), new MyChangeListDeleteProvider());
    }

    @Nullable
    private DeleteProvider selectDelegate(final DataContext dataContext) {
      for (DeleteProvider provider : myProviders) {
        if (provider.canDeleteElement(dataContext)) {
          return provider;
        }
      }
      return null;
    }

    public void deleteElement(DataContext dataContext) {
      final DeleteProvider delegate = selectDelegate(dataContext);
      if (delegate != null) {
        delegate.deleteElement(dataContext);
      }
    }

    public boolean canDeleteElement(DataContext dataContext) {
      return selectDelegate(dataContext) != null;
    }
  }

}
