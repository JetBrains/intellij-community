package org.jetbrains.plugins.github.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Tag;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubApiUtil;

import javax.swing.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 */
@Tag("GitHub")
public class GitHubRepository extends BaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.github.GitHubRepository");

  private Pattern myPattern;
  private String myRepoAuthor;
  private String myRepoName;

  public static final String GITHUB_HOST = "https://github.com";

  {
    setUrl(GITHUB_HOST);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public GitHubRepository() {}

  public GitHubRepository(GitHubRepository other) {
    super(other);
    setRepoName(other.myRepoName);
    setRepoAuthor(other.myRepoAuthor);
  }

  public GitHubRepository(GitHubRepositoryType type) {
    super(type);
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 10, 0);
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() &&
           StringUtil.isNotEmpty(getRepoName());
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
    return getIssues(query);
  }

  @Override
  public String getUrl() {
    return GITHUB_HOST;
  }

  @NotNull
  private Task[] getIssues(@Nullable String query) throws Exception {
    String path;
    boolean noQuery = StringUtil.isEmpty(query);
    if (!noQuery) {
      query = encodeUrl(query);
      path = "/legacy/issues/search/" + getRepoAuthor() + "/" + getRepoName() + "/open/" + encodeUrl(query);
    }
    else {
      path = "/repos/" + getRepoAuthor() + "/" + getRepoName() + "/issues";
    }

    JsonElement response = GithubApiUtil.getRequest(getUrl(), getUsername(), getPassword(), path);

    JsonArray issuesArray;
    if (noQuery) {
      if (response == null || !response.isJsonArray()) {
        throw errorFetchingIssues(response);
      }
      issuesArray = response.getAsJsonArray();
    }
    else {
      if (response == null || !response.isJsonObject() || !response.getAsJsonObject().has("issues")) {
        throw errorFetchingIssues(response);
      }
      issuesArray = response.getAsJsonObject().get("issues").getAsJsonArray();
    }

    return parseTasksFromArray(issuesArray);
  }

  @NotNull
  private Task[] parseTasksFromArray(JsonArray issuesArray) {
    List<Task> tasks = new ArrayList<Task>();
    for (JsonElement element : issuesArray) {
      Task issue = createIssue(element.getAsJsonObject());
      if (issue == null) {
        LOG.warn("Couldn't parse issue from " + element);
      }
      else {
        tasks.add(issue);
      }
    }
    return tasks.toArray(new Task[tasks.size()]);
  }

  @NotNull
  private Exception errorFetchingIssues(@Nullable JsonElement response) {
    return new Exception(String.format("Error fetching issues for: %s%nResponse: %s", getUrl(), response));
  }

  @Nullable
  private Task createIssue(JsonObject issueObject) {
    final JsonElement id = issueObject.get("number");
    if (id == null) {
      return null;
    }
    final JsonElement summary = issueObject.get("title");
    if (summary == null) {
      return null;
    }
    JsonElement state = issueObject.get("state");
    if (state == null) {
      return null;
    }
    final boolean isClosed = !"open".equals(state.getAsString());
    final JsonElement description = issueObject.get("body");
    final Ref<Date> updated = new Ref<Date>();
    final Ref<Date> created = new Ref<Date>();
    try {
      JsonElement updatedAt = issueObject.get("updated_at");
      if (updatedAt != null) {
        updated.set(TaskUtil.parseDate(updatedAt.getAsString()));
      }
      else {
        LOG.warn("Couldn't find 'updated-at' field for the issue: " + issueObject);
      }
      JsonElement createdAt = issueObject.get("created_at");
      if (createdAt != null) {
        created.set(TaskUtil.parseDate(createdAt.getAsString()));
      }
      else {
        LOG.warn("Couldn't find 'created-at' field for the issue: " + issueObject);
      }
    } catch (ParseException e) {
      LOG.warn(e);
    }

    return new Task() {
      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        final String id = getRealId(getId());
        return id != null ? getUrl() + "/" + getRepoAuthor() + "/" + myRepoName + "/issues/issue/" + id : null;
      }

      @NotNull
      @Override
      public String getId() {
        return myRepoName + "-" + id;
      }

      @NotNull
      @Override
      public String getSummary() {
        return summary.getAsString();
      }

      public String getDescription() {
        return description.getAsString();
      }

      @NotNull
      @Override
      public Comment[] getComments() {
        try {
          return fetchComments(id.getAsString());
        } catch (Exception e) {
          LOG.warn("Error fetching comments for " + id, e);
        }
        return Comment.EMPTY_ARRAY;
      }

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
        return updated.get();
      }

      @Override
      public Date getCreated() {
        return created.get();
      }

      @Override
      public boolean isClosed() {
        return isClosed;
      }

      @Override
      public TaskRepository getRepository() {
        return GitHubRepository.this;
      }

      @Override
      public String getPresentableName() {
        return getId() + ": " + getSummary();
      }
    };
  }

  private Comment[] fetchComments(final String id) throws Exception {
    String path = "/repos/" + getRepoAuthor() + "/" + getRepoName() + "/issues/" + id + "/comments";
    JsonElement response = GithubApiUtil.getRequest(getUrl(), getUsername(), getPassword(), path);
    if (response == null || !response.isJsonArray()) {
      throw new Exception(String.format("Couldn't get information about issue %s%nResponse: %s", id, response));
    }
    return createComments(response.getAsJsonArray());
  }

  private static Comment[] createComments(final JsonArray response) {
    final List<Comment> comments = new ArrayList<Comment>();

    for (JsonElement element : response) {
      Comment comment = parseComment(element);
      if (comment != null) {
        comments.add(comment);
      }
      else {
        LOG.warn("Couldn't parse comment from " + element);
      }
    }
    return ArrayUtil.toObjectArray(comments, Comment.class);
  }

  @Nullable
  private static Comment parseComment(JsonElement element) {
    JsonObject commentObject = element.getAsJsonObject();
    final JsonElement text = commentObject.get("body");
    if (text == null) {
      return null;
    }
    JsonElement user = commentObject.get("user");
    if (user == null || !user.isJsonObject()) {
      return null;
    }

    final JsonElement author = user.getAsJsonObject().get("login");
    final JsonElement gravatar = user.getAsJsonObject().get("gravatar_id");
    final Ref<Date> date = new Ref<Date>();
    try {
      JsonElement createdAt = commentObject.get("created_at");
      if (createdAt != null) {
        date.set(TaskUtil.parseDate(createdAt.getAsString()));
      }
      else {
        LOG.warn("Couldn't get creation date for the comment: " + element);
      }
    }
    catch (ParseException e) {
      LOG.warn(e);
    }
    return new GitHubComment(date.get(), author == null ? null : author.getAsString(),
                             text.getAsString(), gravatar == null ? null : gravatar.getAsString());
  }

  @Nullable
  private String getRealId(String id) {
    final String start = myRepoName + "-";
    return id.startsWith(start) ? id.substring(start.length()) : null;
  }

  @Nullable
  public String extractId(String taskName) {
    Matcher matcher = myPattern.matcher(taskName);
    return matcher.find() ? matcher.group(1) : null;
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    String path = "/repos/" + getRepoAuthor() + "/" + getRepoName() + "/issues/" + id;
    JsonElement response = GithubApiUtil.getRequest(getUrl(), getUsername(), getPassword(), path);
    if (response == null || !response.isJsonObject()) {
      throw new Exception(String.format("Couldn't get information about issue %s%nResponse: %s", id, response));
    }
    return createIssue(response.getAsJsonObject());
  }

  @Override
  public BaseRepository clone() {
    return new GitHubRepository(this);
  }

  public String getRepoName() {
    return myRepoName;
  }

  public void setRepoName(String repoName) {
    myRepoName = repoName;
    myPattern = Pattern.compile("(" + repoName + "\\-\\d+):\\s+");
  }

  public String getRepoAuthor() {
    return !StringUtil.isEmpty(myRepoAuthor) ? myRepoAuthor : getUsername();
  }

  public void setRepoAuthor(String repoAuthor) {
    myRepoAuthor = repoAuthor;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof GitHubRepository)) return false;

    GitHubRepository that = (GitHubRepository)o;
    if (getRepoName() != null ? !getRepoName().equals(that.getRepoName()) : that.getRepoName() != null) return false;
    return true;
  }
}
