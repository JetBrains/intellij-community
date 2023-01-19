// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.tasks;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubIssue;
import org.jetbrains.plugins.github.api.data.GithubIssueBase;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jetbrains.plugins.github.issue.GithubIssuesLoadingHelper;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Tag("GitHub")
final class GithubRepository extends BaseRepository {

  private Pattern myPattern = Pattern.compile("($^)");
  @NotNull private String myRepoAuthor = "";
  @NotNull private String myRepoName = "";
  @NotNull private String myUser = "";
  private boolean myAssignedIssuesOnly = false;

  @SuppressWarnings({"UnusedDeclaration"})
  GithubRepository() {
  }

  GithubRepository(GithubRepository other) {
    super(other);
    setRepoName(other.myRepoName);
    setRepoAuthor(other.myRepoAuthor);
    setAssignedIssuesOnly(other.myAssignedIssuesOnly);
  }

  GithubRepository(GithubRepositoryType type) {
    super(type);
    setUrl("https://" + GithubServerPath.DEFAULT_HOST);
  }

  @NotNull
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      private final GithubApiRequestExecutor myExecutor = getExecutor();
      private final ProgressIndicator myIndicator = new EmptyProgressIndicator();

      @Override
      protected void doTest() throws Exception {
        try {
          myExecutor.execute(myIndicator, GithubApiRequests.Repos.get(getServer(), getRepoAuthor(), getRepoName()));
        }
        catch (ProcessCanceledException ignore) {
        }
      }

      @Override
      public void cancel() {
        myIndicator.cancel();
      }
    };
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() &&
           !StringUtil.isEmptyOrSpaces(getRepoAuthor()) &&
           !StringUtil.isEmptyOrSpaces(getRepoName()) &&
           !StringUtil.isEmptyOrSpaces(getPassword());
  }

  @Override
  public String getPresentableName() {
    final String name = super.getPresentableName();
    return name +
           (!StringUtil.isEmpty(getRepoAuthor()) ? "/" + getRepoAuthor() : "") +
           (!StringUtil.isEmpty(getRepoName()) ? "/" + getRepoName() : "");
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    try {
      return getIssues(query, offset + limit, withClosed);
    }
    catch (GithubRateLimitExceededException e) {
      return Task.EMPTY_ARRAY;
    }
    catch (GithubAuthenticationException | GithubStatusCodeException e) {
      throw new Exception(e.getMessage(), e); // Wrap to show error message
    }
    catch (GithubJsonException e) {
      throw new Exception("Bad response format", e);
    }
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed, @NotNull ProgressIndicator cancelled)
    throws Exception {
    return getIssues(query, offset, limit, withClosed);
  }

  private Task @NotNull [] getIssues(@Nullable String query, int max, boolean withClosed) throws Exception {
    GithubApiRequestExecutor executor = getExecutor();
    ProgressIndicator indicator = getProgressIndicator();
    GithubServerPath server = getServer();

    String assigned = null;
    if (myAssignedIssuesOnly) {
      if (StringUtil.isEmptyOrSpaces(myUser)) {
        myUser = executor.execute(indicator, GithubApiRequests.CurrentUser.get(server)).getLogin();
      }
      assigned = myUser;
    }

    List<? extends GithubIssueBase> issues;
    if (StringUtil.isEmptyOrSpaces(query)) {
      // search queries have way smaller request number limit
      issues = GithubIssuesLoadingHelper.load(executor, indicator, server, getRepoAuthor(), getRepoName(), withClosed, max, assigned);
    }
    else {
      issues = GithubIssuesLoadingHelper.search(executor, indicator, server, getRepoAuthor(), getRepoName(), withClosed, assigned, query);
    }
    List<Task> tasks = new ArrayList<>();

    for (GithubIssueBase issue : issues) {
      List<GithubIssueCommentWithHtml> comments = GithubApiPagesLoader
        .loadAll(executor, indicator, GithubApiRequests.Repos.Issues.Comments.pages(issue.getCommentsUrl()));
      tasks.add(createTask(issue, comments));
    }

    return tasks.toArray(Task.EMPTY_ARRAY);
  }

  @NotNull
  private Task createTask(@NotNull GithubIssueBase issue, @NotNull List<GithubIssueCommentWithHtml> comments) {
    return new Task() {
      @NotNull private final String myRepoName = getRepoName();
      private final Comment @NotNull [] myComments =
        ContainerUtil.map2Array(comments, Comment.class, comment -> new GithubComment(comment.getCreatedAt(),
                                                                                      comment.getUser().getLogin(),
                                                                                      comment.getBodyHtml(),
                                                                                      comment.getUser().getAvatarUrl(),
                                                                                      comment.getUser().getHtmlUrl()));

      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        return issue.getHtmlUrl();
      }

      @Override
      public @NlsSafe @NotNull String getId() {
        return myRepoName + "-" + issue.getNumber();
      }

      @NotNull
      @Override
      public String getSummary() {
        return issue.getTitle();
      }

      @Override
      public String getDescription() {
        return issue.getBody();
      }

      @Override
      public Comment @NotNull [] getComments() {
        return myComments;
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return AllIcons.Vcs.Vendors.Github;
      }

      @NotNull
      @Override
      public TaskType getType() {
        return TaskType.BUG;
      }

      @Override
      public Date getUpdated() {
        return issue.getUpdatedAt();
      }

      @Override
      public Date getCreated() {
        return issue.getCreatedAt();
      }

      @Override
      public boolean isClosed() {
        return issue.getState() == GithubIssueState.closed;
      }

      @Override
      public TaskRepository getRepository() {
        return GithubRepository.this;
      }

      @Override
      public String getPresentableName() {
        return getId() + ": " + getSummary();
      }
    };
  }

  @Override
  @Nullable
  public String extractId(@NotNull String taskName) {
    Matcher matcher = myPattern.matcher(taskName);
    return matcher.find() ? matcher.group(1) : null;
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    final int index = id.lastIndexOf("-");
    if (index < 0) {
      return null;
    }
    final String numericId = id.substring(index + 1);
    GithubApiRequestExecutor executor = getExecutor();
    ProgressIndicator indicator = getProgressIndicator();
    GithubIssue issue = executor.execute(indicator,
                                         GithubApiRequests.Repos.Issues.get(getServer(), getRepoAuthor(), getRepoName(), numericId));
    if (issue == null) return null;
    List<GithubIssueCommentWithHtml> comments = GithubApiPagesLoader
      .loadAll(executor, indicator, GithubApiRequests.Repos.Issues.Comments.pages(issue.getCommentsUrl()));
    return createTask(issue, comments);
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull TaskState state) throws Exception {
    boolean isOpen = switch (state) {
      case OPEN -> true;
      case RESOLVED -> false;
      default -> throw new IllegalStateException("Unknown state: " + state);
    };
    GithubApiRequestExecutor executor = getExecutor();
    GithubServerPath server = getServer();
    String repoAuthor = getRepoAuthor();
    String repoName = getRepoName();

    ProgressIndicator indicator = getProgressIndicator();
    executor.execute(indicator,
                     GithubApiRequests.Repos.Issues.updateState(server, repoAuthor, repoName, task.getNumber(), isOpen));
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new GithubRepository(this);
  }

  public @NlsSafe @NotNull String getRepoName() {
    return myRepoName;
  }

  public void setRepoName(@NotNull String repoName) {
    myRepoName = repoName;
    myPattern = Pattern.compile("(" + StringUtil.escapeToRegexp(repoName) + "\\-\\d+)");
  }

  public @NlsSafe @NotNull String getRepoAuthor() {
    return myRepoAuthor;
  }

  public void setRepoAuthor(@NotNull String repoAuthor) {
    myRepoAuthor = repoAuthor;
  }

  public @NlsSafe @NotNull String getUser() {
    return myUser;
  }

  public void setUser(@NotNull String user) {
    myUser = user;
  }

  /**
   * Stores access token
   */
  @Override
  public void setPassword(String password) {
    super.setPassword(password);
    setUser("");
  }

  public boolean isAssignedIssuesOnly() {
    return myAssignedIssuesOnly;
  }

  public void setAssignedIssuesOnly(boolean value) {
    myAssignedIssuesOnly = value;
  }

  @Override
  @NotNull
  protected CredentialAttributes getAttributes() {
    String serviceName = CredentialAttributesKt.generateServiceName("Tasks", getRepositoryType().getName() + " " + getPresentableName());
    return new CredentialAttributes(serviceName, "GitHub OAuth token");
  }

  @NotNull
  private GithubApiRequestExecutor getExecutor() {
    return GithubApiRequestExecutor.Factory.getInstance().create(getPassword(), myUseProxy);
  }

  @NotNull
  private static ProgressIndicator getProgressIndicator() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) indicator = new EmptyProgressIndicator();
    return indicator;
  }

  @NotNull
  private GithubServerPath getServer() {
    return GithubServerPath.from(getUrl());
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof GithubRepository)) return false;

    GithubRepository that = (GithubRepository)o;
    if (!Objects.equals(getRepoAuthor(), that.getRepoAuthor())) return false;
    if (!Objects.equals(getRepoName(), that.getRepoName())) return false;
    if (!Comparing.equal(isAssignedIssuesOnly(), that.isAssignedIssuesOnly())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return StringUtil.stringHashCode(getRepoName()) +
           31 * StringUtil.stringHashCode(getRepoAuthor());
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | STATE_UPDATING;
  }
}
