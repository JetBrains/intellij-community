package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubConnection;
import org.jetbrains.plugins.github.api.GithubIssue;
import org.jetbrains.plugins.github.api.GithubIssueComment;
import org.jetbrains.plugins.github.exceptions.*;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 */
@Tag("GitHub")
public class GithubRepository extends BaseRepositoryImpl {
  private static final Logger LOG = GithubUtil.LOG;

  private Pattern myPattern = Pattern.compile("($^)");
  @NotNull private String myRepoAuthor = "";
  @NotNull private String myRepoName = "";
  @NotNull private String myUser = "";
  @NotNull private String myToken = "";
  private boolean myAssignedIssuesOnly = false;

  @SuppressWarnings({"UnusedDeclaration"})
  public GithubRepository() {
  }

  public GithubRepository(GithubRepository other) {
    super(other);
    setRepoName(other.myRepoName);
    setRepoAuthor(other.myRepoAuthor);
    setToken(other.myToken);
    setAssignedIssuesOnly(other.myAssignedIssuesOnly);
  }

  public GithubRepository(GithubRepositoryType type) {
    super(type);
    setUrl("https://" + GithubApiUtil.DEFAULT_GITHUB_HOST);
  }

  @NotNull
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      private final GithubConnection myConnection = new GithubConnection(getAuthData(), false);

      @Override
      protected void doTest() throws Exception {
        try {
          GithubApiUtil.getIssuesQueried(myConnection, getRepoAuthor(), getRepoName(), null, null, false);
        }
        catch (GithubOperationCanceledException ignore) {
        }
      }

      @Override
      public void cancel() {
        myConnection.abort();
      }
    };
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() &&
           !StringUtil.isEmptyOrSpaces(getRepoAuthor()) &&
           !StringUtil.isEmptyOrSpaces(getRepoName()) &&
           !StringUtil.isEmptyOrSpaces(getToken());
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
      return new Task[0];
    }
    catch (GithubAuthenticationException e) {
      throw new Exception(e.getMessage(), e); // Wrap to show error message
    }
    catch (GithubStatusCodeException e) {
      throw new Exception(e.getMessage(), e);
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

  @NotNull
  private Task[] getIssues(@Nullable String query, int max, boolean withClosed) throws Exception {
    GithubConnection connection = getConnection();

    try {
      String assigned = null;
      if (myAssignedIssuesOnly) {
        if (StringUtil.isEmptyOrSpaces(myUser)) {
          myUser = GithubApiUtil.getCurrentUser(connection).getLogin();
        }
        assigned = myUser;
      }

      List<GithubIssue> issues;
      if (StringUtil.isEmptyOrSpaces(query)) {
        // search queries have way smaller request number limit
        issues =
          GithubApiUtil.getIssuesAssigned(connection, getRepoAuthor(), getRepoName(), assigned, max, withClosed);
      }
      else {
        issues =
          GithubApiUtil.getIssuesQueried(connection, getRepoAuthor(), getRepoName(), assigned, query, withClosed);
      }

      return ContainerUtil.map2Array(issues, Task.class, new Function<GithubIssue, Task>() {
        @Override
        public Task fun(GithubIssue issue) {
          return createTask(issue);
        }
      });
    }
    finally {
      connection.close();
    }
  }

  @NotNull
  private Task createTask(final GithubIssue issue) {
    return new Task() {
      @NotNull String myRepoName = getRepoName();

      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        return issue.getHtmlUrl();
      }

      @NotNull
      @Override
      public String getId() {
        return myRepoName + "-" + issue.getNumber();
      }

      @NotNull
      @Override
      public String getSummary() {
        return issue.getTitle();
      }

      public String getDescription() {
        return issue.getBody();
      }

      @NotNull
      @Override
      public Comment[] getComments() {
        try {
          return fetchComments(issue.getNumber());
        }
        catch (Exception e) {
          LOG.warn("Error fetching comments for " + issue.getNumber(), e);
          return Comment.EMPTY_ARRAY;
        }
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return TasksIcons.Github;
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
        return !"open".equals(issue.getState());
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

  private Comment[] fetchComments(final long id) throws Exception {
    GithubConnection connection = getConnection();
    try {
      List<GithubIssueComment> result = GithubApiUtil.getIssueComments(connection, getRepoAuthor(), getRepoName(), id);

      return ContainerUtil.map2Array(result, Comment.class, new Function<GithubIssueComment, Comment>() {
        @Override
        public Comment fun(GithubIssueComment comment) {
          return new GithubComment(comment.getCreatedAt(), comment.getUser().getLogin(), comment.getBodyHtml(),
                                   comment.getUser().getAvatarUrl(),
                                   comment.getUser().getHtmlUrl());
        }
      });
    }
    finally {
      connection.close();
    }
  }

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
    GithubConnection connection = getConnection();
    try {
      return createTask(GithubApiUtil.getIssue(connection, getRepoAuthor(), getRepoName(), numericId));
    }
    catch (GithubStatusCodeException e) {
      if (e.getStatusCode() == 404) {
        return null;
      }
      throw e;
    }
    finally {
      connection.close();
    }
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull TaskState state) throws Exception {
    GithubConnection connection = getConnection();
    try {
      boolean isOpen;
      switch (state) {
        case OPEN:
          isOpen = true;
          break;
        case RESOLVED:
          isOpen = false;
          break;
        default:
          throw new IllegalStateException("Unknown state: " + state);
      }
      GithubApiUtil.setIssueState(connection, getRepoAuthor(), getRepoName(), task.getNumber(), isOpen);
    }
    finally {
      connection.close();
    }
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new GithubRepository(this);
  }

  @NotNull
  public String getRepoName() {
    return myRepoName;
  }

  public void setRepoName(@NotNull String repoName) {
    myRepoName = repoName;
    myPattern = Pattern.compile("(" + StringUtil.escapeToRegexp(repoName) + "\\-\\d+)");
  }

  @NotNull
  public String getRepoAuthor() {
    return myRepoAuthor;
  }

  public void setRepoAuthor(@NotNull String repoAuthor) {
    myRepoAuthor = repoAuthor;
  }

  @NotNull
  public String getUser() {
    return myUser;
  }

  public void setUser(@NotNull String user) {
    myUser = user;
  }

  @Transient
  @NotNull
  public String getToken() {
    return myToken;
  }

  public void setToken(@NotNull String token) {
    myToken = token;
    setUser("");
  }

  public boolean isAssignedIssuesOnly() {
    return myAssignedIssuesOnly;
  }

  public void setAssignedIssuesOnly(boolean value) {
    myAssignedIssuesOnly = value;
  }

  @Tag("token")
  public String getEncodedToken() {
    return PasswordUtil.encodePassword(getToken());
  }

  public void setEncodedToken(String password) {
    try {
      setToken(PasswordUtil.decodePassword(password));
    }
    catch (NumberFormatException e) {
      LOG.warn("Can't decode token", e);
    }
  }

  private GithubAuthData getAuthData() {
    return GithubAuthData.createTokenAuth(getUrl(), getToken(), isUseProxy());
  }

  private GithubConnection getConnection() {
    return new GithubConnection(getAuthData(), true);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof GithubRepository)) return false;

    GithubRepository that = (GithubRepository)o;
    if (!Comparing.equal(getRepoAuthor(), that.getRepoAuthor())) return false;
    if (!Comparing.equal(getRepoName(), that.getRepoName())) return false;
    if (!Comparing.equal(getToken(), that.getToken())) return false;
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
