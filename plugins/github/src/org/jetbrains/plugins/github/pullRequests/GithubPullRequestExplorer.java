/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullRequests;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ui.UIUtil;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubPullRequest;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

public class GithubPullRequestExplorer extends JPanel {
  private static final Logger LOG = Logger.getInstance(GithubPullRequestExplorer.class);
  private final Project myProject;
  private final GithubAuthDataHolder myAuthDataHolder;
  private final ChangesBrowser myChangesBrowser;
  private final JBLoadingPanel myChangesPanel;
  private JBList<GithubPullRequest> myPullRequestsList;
  private static final String PR_PREFIX = "PR Details";

  public GithubPullRequestExplorer(@NotNull final Project project, @NotNull final List<GithubPullRequest> requests,
                                   @NotNull final GithubAuthDataHolder authHolder) {
    super(new BorderLayout());
    myProject = project;
    myAuthDataHolder = authHolder;

    final JPanel pullRequestsPanel = createPullRequestsPanel(requests);

    myChangesPanel = new JBLoadingPanel(new BorderLayout(), myProject);
    myChangesBrowser = new RepositoryChangesBrowser(project, Collections.emptyList());
    myChangesPanel.add(myChangesBrowser);

    final Splitter pullRequestsComponent = new Splitter(false, 0.5f);
    pullRequestsComponent.setFirstComponent(pullRequestsPanel);
    pullRequestsComponent.setSecondComponent(myChangesPanel);

    add(pullRequestsComponent, BorderLayout.CENTER);
  }

  @NotNull
  private JPanel createPullRequestsPanel(@NotNull final List<GithubPullRequest> requests) {
    myPullRequestsList = new JBList<>(requests);
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myPullRequestsList);
    decorator.disableUpDownActions();
    decorator.disableRemoveAction();
    decorator.disableAddAction();
    decorator.setToolbarPosition(ActionToolbarPosition.TOP);
    decorator.addExtraAction(new MergePullRequestAction());
    decorator.addExtraAction(new OpenInBrowserAction());
    myPullRequestsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myPullRequestsList.setCellRenderer(new ColoredListCellRenderer<GithubPullRequest>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends GithubPullRequest> list, GithubPullRequest value, int index,
                                           boolean selected, boolean hasFocus) {
        final String state = value.getState();
        //TODO: add some filtering/sorting to remove closed requests
        final SimpleTextAttributes textAttributes = GithubPullRequest.State.CLOSED.equalsType(state)
                                                    ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null)
                                                    : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        append(value.getTitle(), textAttributes);
        if (!selected && index % 2 == 0) {
          setBackground(UIUtil.getDecoratedRowColor());
        }
      }
    });

    myPullRequestsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          final GithubPullRequest request = myPullRequestsList.getSelectedValue(); //TODO: cache files diff
          updatePullRequestChanges(request);
        }
      }
    });
    myPullRequestsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          openRequestDetails(myPullRequestsList.getSelectedValue());
        }
      }
    });
    myPullRequestsList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          openRequestDetails(myPullRequestsList.getSelectedValue());
        }
      }
    });
    return decorator.createPanel();
  }

  private void openRequestDetails(@NotNull final GithubPullRequest pullRequest) {
    final GithubPullRequestDetailsPanel details = new GithubPullRequestDetailsPanel(myProject, pullRequest, myAuthDataHolder);
    ToolWindow changes = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);
    ContentUtilEx.addTabbedContent(changes.getContentManager(), details, PR_PREFIX, pullRequest.getTitle(), true);
  }

  private void updatePullRequestChanges(@NotNull final GithubPullRequest request) {
    myChangesPanel.startLoading();
    new Task.Backgroundable(myProject, "Loading Files Diff For Request " + request.getNumber()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String diff = getDiff(request);
        final PatchReader reader = new PatchReader(diff);
        try {
          reader.parseAllPatches();
        }
        catch (PatchSyntaxException e) {
          LOG.error("Failed to parse patches");
        }
        final List<TextFilePatch> patches = reader.getTextPatches();
        final List<AbstractFilePatchInProgress> matchedPatches =
          new MatchPatchPaths(myProject).execute(patches, true);
        final List<Change> changes = map(matchedPatches, AbstractFilePatchInProgress::getChange);
        myChangesBrowser.setChangesToDisplay(changes);
        myChangesPanel.stopLoading();
      }
    }.queue();
  }

  private String getDiff(@NotNull final GithubPullRequest request) {
    try {
      //TODO: sometimes there are no diff
      final String diffUrl = request.getDiffUrl();
      return GithubUtil
        .runTask(myProject, myAuthDataHolder, new EmptyProgressIndicator(), connection -> connection.getRequestPlain(diffUrl));
    }
    catch (Exception e) {
      LOG.error("Failed to get files diff " + e.getMessage());
      return null;
    }
  }

  private class MergePullRequestAction extends AnActionButton {

    public MergePullRequestAction() {
      super("Merge Pull Request", AllIcons.Actions.Stub);
    }

    @Override
    public boolean isEnabled() {
      //TODO: allow to merge only if user owns repository
      final GithubPullRequest request = myPullRequestsList.getSelectedValue();
      return request != null && GithubPullRequest.State.OPEN.equalsType(request.getState());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final GithubPullRequest request = myPullRequestsList.getSelectedValue();
      GithubUtil.computeValueInModal(myProject, "Fetching repository...", indicator -> {
        try {
          return GithubUtil.runTask(myProject, myAuthDataHolder, indicator, connection -> {
            final GithubPullRequest.Link base = request.getBase();
            final String name = base.getRepo() != null ? base.getRepo().getName() : "";

            //TODO: show dialog for title/message
            String title = "title";
            final String message = "Message";

            return GithubApiUtil.mergePullRequest(connection, base.getUser().getLogin(), name,
                                                  String.valueOf(request.getNumber()), title, message,
                                                  request.getHead().getSha());
          });
        }
        catch (IOException e1) {
          LOG.error("Failed to merge pull request: " + e1.getMessage());
          return null;
        }
      });

      //TODO: suggest to remove corresponding local branch? Or should we do it silently?
    }
  }

  private class OpenInBrowserAction extends AnActionButton {
    public OpenInBrowserAction() {
      super("Open In Browser", GithubIcons.Github_icon);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final GithubPullRequest request = myPullRequestsList.getSelectedValue();
      final GithubPullRequest.Link base = request.getBase();
      final String name = base.getRepo() != null ? base.getRepo().getName() : "";
      final String uri = "https://github.com/" + base.getUser().getLogin() + "/" +
                         name + "/pull/" + request.getNumber();
      BrowserUtil.browse(uri);
    }

    @Override
    public boolean isEnabled() {
      final GithubPullRequest request = myPullRequestsList.getSelectedValue();
      return request != null;
    }
  }
}
