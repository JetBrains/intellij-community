// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.tasks;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.data.GithubIssueBase;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GithubRepositoryTest {
  private GithubRepository repository;

  @BeforeEach
  public void setUp() {
    repository = new GithubRepository();
  }

  @Test
  public void testCreateTaskBug() {
    GithubIssueBase issue = makeIssueWithLabels(Collections.singletonList("bug"));
    Task task = createTask(repository, issue, Collections.emptyList());
    assertEquals(TaskType.BUG, task.getType());
  }

  @Test
  public void testCreateTaskFeature() {
    GithubIssueBase issue = makeIssueWithLabels(Collections.singletonList("enhancement"));
    Task task = createTask(repository, issue, Collections.emptyList());
    assertEquals(TaskType.FEATURE, task.getType());
  }

  @Test
  public void testCreateTaskUnmapped() {
    // Test with a label that is not recognized as a specific type
    GithubIssueBase issue = makeIssueWithLabels(Collections.singletonList("question"));
    Task task = createTask(repository, issue, Collections.emptyList());
    assertEquals(TaskType.OTHER, task.getType());
  }

  @Test
  public void testCreateTaskMultipleLabelsMatchFirstWins() {
    GithubIssueBase issue = makeIssueWithLabels(
      List.of("bug", "enhancement", "feature", "task", "question") // Multiple labels including bug and enhancement
    );
    Task task = createTask(repository, issue, Collections.emptyList());
    assertEquals(TaskType.BUG, task.getType());
  }

  @Test
  public void testCreateTaskMultipleLabelsSingleMatch() {
    GithubIssueBase issue = makeIssueWithLabels(
      List.of("custom1", "enhancement", "custom2") // Multiple labels including enhancement in the middle
    );
    Task task = createTask(repository, issue, Collections.emptyList());
    assertEquals(TaskType.FEATURE, task.getType());
  }

  @Test
  public void testCreateTaskNoLabel() {
    GithubIssueBase issue = makeIssueWithLabels(
      Collections.emptyList()
    );
    Task task = createTask(repository, issue, Collections.emptyList());
    assertEquals(TaskType.OTHER, task.getType());
  }

  /**
   * Helper method to create a GithubIssueLabel instance for testing.
   *
   * @param name the name of the label
   * @return a new instance of GithubIssueLabel with the specified name
   */
  public GithubIssueLabel makeLabel(String name) {
    return new GithubIssueLabel() {
      @Override
      public @NotNull String getName() {
        return name;
      }
    };
  }

  /**
   * Helper method to create a GithubIssueBase instance with specified labels.
   *
   * @param labelNames the names of the labels to be associated with the issue
   * @return a new instance of GithubIssueBase with the specified labels
   */
  public GithubIssueBase makeIssueWithLabels(@NotNull List<String> labelNames) {
    List<GithubIssueLabel> labels = ContainerUtil.map(labelNames, name -> makeLabel(name));
    return new GithubIssueBase() {
      @Override
      public List<GithubIssueLabel> getLabels() {
        return labels;
      }
    };
  }

  /**
   * Helper method to invoke the private createTask method using reflection.
   *
   * @param repo     the GithubRepository instance
   * @param issue    the GithubIssueBase instance
   * @param comments the list of comments associated with the issue
   * @return a Task object created by the private createTask method
   */
  private static Task createTask(GithubRepository repo, GithubIssueBase issue, List<GithubIssueCommentWithHtml> comments) {
    Task task = repo.createTask(issue, comments);
    return task;
  }

}