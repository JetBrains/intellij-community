package org.hanuna.gitalk.ui;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.DataPackUtils;
import org.hanuna.gitalk.data.rebase.GitActionHandler;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeModel;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.util.List;

/**
 * @author erokhins
 */
public interface UI_Controller {

  public TableModel getGraphTableModel();

  public void click(@Nullable GraphElement graphElement);

  public void click(int rowIndex);

  public void over(@Nullable GraphElement graphElement);

  public void hideAll();

  public void showAll();

  public void setLongEdgeVisibility(boolean visibility);

  public void updateVisibleBranches();

  public TreeTableModel getRefsTreeTableModel();

  public RefTreeModel getRefTreeModel();

  public void addControllerListener(@NotNull ControllerListener listener);


  public void removeAllListeners();

  public void doubleClick(int rowIndex);

  public void readNextPart();

  public void jumpToCommit(Hash commitHash);

  List<Ref> getRefs();

  @NotNull
  DragDropListener getDragDropListener();

  @NotNull
  InteractiveRebaseBuilder getInteractiveRebaseBuilder();

  @NotNull
  GitActionHandler getGitActionHandler();

  DataPack getDataPack();

  DataPackUtils getDataPackUtils();

  Project getProject();

  void refresh(boolean dontReadFromGit);

  void applyInteractiveRebase();

  GitActionHandler.Callback getCallback();

  void cancelInteractiveRebase();

  GitRepository getRepository();

  boolean isInteractiveRebaseInProgress();
}
