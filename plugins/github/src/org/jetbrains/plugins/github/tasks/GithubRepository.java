package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
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
import org.jetbrains.plugins.github.api.GithubIssue;
import org.jetbrains.plugins.github.api.GithubIssueComment;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
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

  @SuppressWarnings({"UnusedDeclaration"})
  public GithubRepository() {}

  public GithubRepository(GithubRepository other) {
    super(other);
    setRepoName(other.myRepoName);
    setRepoAuthor(other.myRepoAuthor);
    setToken(other.myToken);
  }

  public GithubRepository(GithubRepositoryType type) {
    super(type);
    setUrl(GithubApiUtil.DEFAULT_GITHUB_HOST);
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 10, 0);
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
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    try {
      return getIssues(query, max);
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

  @NotNull
  private Task[] getIssues(@Nullable String query, int max) throws Exception {
    List<GithubIssue> issues;
    if (StringUtil.isEmptyOrSpaces(query)) {
      if (StringUtil.isEmptyOrSpaces(myUser)) {
        myUser = GithubApiUtil.getCurrentUser(getAuthData()).getLogin();
      }
      issues = GithubApiUtil.getIssuesAssigned(getAuthData(), getRepoAuthor(), getRepoName(), myUser, max);
    }
    else {
      issues = GithubApiUtil.getIssuesQueried(getAuthData(), getRepoAuthor(), getRepoName(), query);
    }

    return ContainerUtil.map2Array(issues, Task.class, new Function<GithubIssue, Task>() {
      @Override
      public Task fun(GithubIssue issue) {
        return createTask(issue);
      }
    });
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
    List<GithubIssueComment> result = GithubApiUtil.getIssueComments(getAuthData(), getRepoAuthor(), getRepoName(), id);

    return ContainerUtil.map2Array(result, Comment.class, new Function<GithubIssueComment, Comment>() {
      @Override
      public Comment fun(GithubIssueComment comment) {
        return new GithubComment(comment.getCreatedAt(), comment.getUser().getLogin(), comment.getBodyHtml(), comment.getUser().getGravatarId(),
                                 comment.getUser().getHtmlUrl());
      }
    });
  }

  @Nullable
  public String extractId(String taskName) {
    Matcher matcher = myPattern.matcher(taskName);
    return matcher.find() ? matcher.group(1) : null;
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    return createTask(GithubApiUtil.getIssue(getAuthData(), getRepoAuthor(), getRepoName(), id));
  }

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
    myPattern = Pattern.compile("(" + StringUtil.escapeToRegexp(repoName) + "\\-\\d+):\\s+");
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

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof GithubRepository)) return false;

    GithubRepository that = (GithubRepository)o;
    if (!Comparing.equal(getRepoAuthor(), that.getRepoAuthor())) return false;
    if (!Comparing.equal(getRepoName(), that.getRepoName())) return false;
    if (!Comparing.equal(getToken(), that.getToken())) return false;

    return true;
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | BASIC_HTTP_AUTHORIZATION;
  }
}
