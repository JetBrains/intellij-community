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
package org.jetbrains.plugins.github.pullrequests.overview;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubConnection.PagedRequest;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.data.GithubPullRequest;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.pullrequests.GithubToolWindow;
import org.jetbrains.plugins.github.pullrequests.ui.GithubAuthorAssigneesIconPanel;
import org.jetbrains.plugins.github.pullrequests.util.GithubLoadMoreListModel;
import org.jetbrains.plugins.github.pullrequests.util.GithubWebImageLoader;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubUrlUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GithubPullRequestListPanel extends JPanel implements Disposable {
  @NotNull private final GithubToolWindow myToolWindow;
  @NotNull private final Project myProject;
  @NotNull private final GithubFullPath myRepository;
  @NotNull private final GithubAuthDataHolder myAuthHolder;

  @NotNull private final JBList myList;
  @NotNull private final GithubLoadMoreListModel<GithubPullRequest> myListModel;

  @NotNull private final SearchTextFieldWithStoredHistory mySearchField;

  // TODO - "vcs-log"-like loading line at the top ?
  @NotNull private final AsyncProcessIcon myProgressIcon = new AsyncProcessIcon("GithubPullRequestListPanel");

  @NotNull private final GithubPagedRequestLoader<GithubPullRequest> myPullRequestLoader;
  @NotNull private final GithubWebImageLoader.Listener myImageLoaderListener;

  public GithubPullRequestListPanel(@NotNull GithubToolWindow toolWindow) {
    super(new BorderLayout());
    myToolWindow = toolWindow;
    myProject = myToolWindow.getProject();
    myRepository = myToolWindow.getFullPath();
    myAuthHolder = myToolWindow.getAuthDataHolder();

    myListModel = new GithubLoadMoreListModel<>();
    myList = new JBList(myListModel) {
      @Override
      public int locationToIndex(Point location) {
        int index = super.locationToIndex(location);
        return index >= 0 && getCellBounds(index, index).contains(location) ? index : -1;
      }
    };
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new MyListRenderer());
    new ListSpeedSearch(myList);
    new MyReviewSelectedAction().registerCustomShortcutSet(myList, null);

    mySearchField = new SearchTextFieldWithStoredHistory("github.pullrequest.list.filter");
    mySearchField.getTextEditor().addActionListener(e -> {
      mySearchField.addCurrentTextToHistory();
      loadPullRequests(mySearchField.getText());
    });

    myPullRequestLoader = new GithubPagedRequestLoader<>(myProject, myAuthHolder, this, (requests, hasNext) -> {
      myProgressIcon.suspend();
      myListModel.setElements(requests, hasNext);
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActions(), true);
    toolbar.setTargetComponent(this);

    myImageLoaderListener = new GithubWebImageLoader.Listener() {
      @Override
      public void iconsUpdated() {
        GithubPullRequestListPanel.this.repaint();
      }
    };
    GithubWebImageLoader.getInstance().addListener(myImageLoaderListener);


    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    JPanel topPanel = JBUI.Panels
      .simplePanel(toolbar.getComponent())
      .addToLeft(mySearchField)
      .addToRight(myProgressIcon);

    add(topPanel, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);


    loadPullRequests(null);
  }

  private void loadPullRequests(@Nullable String searchQuery) {
    PagedRequest<GithubPullRequest> request;
    if (StringUtil.isEmptyOrSpaces(searchQuery)) {
      request = GithubApiUtil.getPullRequests(myRepository.getUser(), myRepository.getRepository());
    }
    else {
      request = GithubApiUtil.getPullRequestsQueried(myRepository.getUser(), myRepository.getRepository(), searchQuery);
    }

    boolean started = myPullRequestLoader.loadRequest(request);
    if (started) myProgressIcon.resume();
  }

  @NotNull
  private ActionGroup createToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new OpenInBrowserAction());
    return group;
  }

  @Override
  public void dispose() {
    GithubWebImageLoader.getInstance().removeListener(myImageLoaderListener);
    myPullRequestLoader.abort();
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  private static class MyListRenderer implements ListCellRenderer {
    private final JPanel myReviewPanel = new JPanel(new BorderLayout());
    private final JBLabel myTitleLabel = new JBLabel();
    private final JBLabel myAuthorLabel = new JBLabel();
    private final GithubAuthorAssigneesIconPanel myAuthorIcon = new GithubAuthorAssigneesIconPanel();

    private final JPanel myShowMorePanel = new JPanel(new BorderLayout());
    private final JBLabel myShowMoreLabel = new JBLabel("Show More...");

    public MyListRenderer() {
      float fontSizeBetweenMiniAndSmall = Math.max(UIUtil.getListFont().getSize() - JBUI.scale(4f), JBUI.scale(10f));
      myAuthorLabel.setFont(UIUtil.getListFont().deriveFont(fontSizeBetweenMiniAndSmall));
      myAuthorLabel.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 0, 0));

      myShowMorePanel.setOpaque(true);
      myShowMorePanel.add(myShowMoreLabel, BorderLayout.CENTER);
      myShowMorePanel.setBorder(IdeBorderFactory.createEmptyBorder(4, 4, 4, 4));

      myAuthorIcon.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 0, 10));

      JPanel mainPanel = new NonOpaquePanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      mainPanel.setBorder(IdeBorderFactory.createEmptyBorder(4, 4, 4, 4));
      mainPanel.add(myTitleLabel);
      mainPanel.add(myAuthorLabel);

      myReviewPanel.add(mainPanel, BorderLayout.CENTER);
      myReviewPanel.add(myAuthorIcon, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object element, int index, boolean selected, boolean cellHasFocus) {
      JComponent result;
      Color background = selected ? UIUtil.getListSelectionBackground() : index % 2 == 0 ?
                                                                          UIUtil.getDecoratedRowColor() : UIUtil.getListBackground();
      Color foreground = UIUtil.getListForeground(selected);

      if (element instanceof GithubPullRequest) {
        GithubPullRequest request = (GithubPullRequest)element;
        result = myReviewPanel;
        if (!selected && request.isClosed()) {
          foreground = UIUtil.getLabelDisabledForeground();
        }

        GithubUser user = request.getUser();
        List<GithubUser> assignees = request.getAssignees();

        String title = "#" + request.getNumber() + "  " + request.getTitle();
        String author = "Author: " + user.getLogin();
        if (!assignees.isEmpty()) {
          author += ", " + StringUtil.pluralize("Assignee", assignees.size()) + ": " +
                    StringUtil.join(assignees, it -> it.getLogin(), ", ");
        }

        myTitleLabel.setText(title);
        myAuthorLabel.setText(author);

        myAuthorIcon.setUsers(user, assignees);

        myTitleLabel.setForeground(foreground);
        myAuthorLabel.setForeground(foreground);
      }
      else {
        result = myShowMorePanel;
        myShowMoreLabel.setForeground(foreground);
      }
      result.setBackground(background);
      return result;
    }
  }

  private class MyReviewSelectedAction extends DumbAwareAction {
    public MyReviewSelectedAction() {
      setShortcutSet(new CompositeShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, CommonShortcuts.ENTER));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Object value = myList.getSelectedValue();
      if (value == null) return;
      if (value == GithubLoadMoreListModel.SHOW_MORE_ELEMENT) {
        boolean started = myPullRequestLoader.loadMore();
        if (started) myProgressIcon.resume();
      }
      else {
        GithubPullRequest pullRequest = (GithubPullRequest)value;
        myToolWindow.openPullRequestTab(pullRequest);
      }
    }
  }

  private class OpenInBrowserAction extends DumbAwareAction {
    public OpenInBrowserAction() {
      ActionUtil.copyFrom(this, "Github.Open.In.Browser");
    }

    @Override
    public void update(AnActionEvent e) {
      Object value = myList.getSelectedValue();
      e.getPresentation().setEnabled(value instanceof GithubPullRequest);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GithubPullRequest pullRequest = (GithubPullRequest)myList.getSelectedValue();
      String githubUrl = GithubUrlUtil.getGithubHost() + '/' + myRepository.getUser() + '/'
                         + myRepository.getRepository() + "/pull/" + pullRequest.getNumber();
      BrowserUtil.browse(githubUrl);
    }
  }
}
