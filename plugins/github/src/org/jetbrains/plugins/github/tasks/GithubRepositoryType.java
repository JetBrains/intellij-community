package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dennis.Ushakov
 */
public class GithubRepositoryType extends BaseRepositoryType<GithubRepository> {

  @NotNull
  @Override
  public String getName() {
    return "GitHub";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Github;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new GithubRepository(this);
  }

  @Override
  public Class<GithubRepository> getRepositoryClass() {
    return GithubRepository.class;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(GithubRepository repository,
                                           Project project,
                                           Consumer<GithubRepository> changeListener) {
    return new GithubRepositoryEditor(project, repository, changeListener);
  }

  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.OPEN, TaskState.RESOLVED);
  }

}
