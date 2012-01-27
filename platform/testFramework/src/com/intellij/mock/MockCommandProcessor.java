package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockCommandProcessor extends CommandProcessor {
  @Override
  public void executeCommand(@NotNull Runnable runnable, String name, Object groupId) {
  }

  @Override
  public void executeCommand(Project project, @NotNull Runnable runnable, String name, Object groupId) {
  }

  @Override
  public void executeCommand(Project project,
                             @NotNull Runnable runnable,
                             String name,
                             Object groupId,
                             @NotNull UndoConfirmationPolicy confirmationPolicy) {

  }

  @Override
  public void setCurrentCommandName(String name) {
  }

  @Override
  public void setCurrentCommandGroupId(Object groupId) {
  }

  @Override
  public Runnable getCurrentCommand() {
    return null;
  }

  @Override
  public String getCurrentCommandName() {
    return null;
  }

  @Override
  @Nullable
  public Object getCurrentCommandGroupId() {
    return null;
  }

  @Override
  public Project getCurrentCommandProject() {
    return null;
  }

  @Override
  public void addCommandListener(@NotNull CommandListener listener) {
  }

  @Override
  public void addCommandListener(@NotNull CommandListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeCommandListener(@NotNull CommandListener listener) {
  }

  @Override
  public boolean isUndoTransparentActionInProgress() {
    return false;
  }

  @Override
  public void markCurrentCommandAsGlobal(Project project) {

  }

  @Override
  public void runUndoTransparentAction(@NotNull Runnable action) {
  }

  @Override
  public void executeCommand(Project project, @NotNull Runnable command, String name, Object groupId, @NotNull UndoConfirmationPolicy confirmationPolicy,
                             Document document) {
  }

  @Override
  public void executeCommand(Project project, @NotNull Runnable runnable, @Nls String name, Object groupId, @Nullable Document document) {

  }

  @Override
  public void addAffectedDocuments(Project project, @NotNull Document... docs) {
  }

  @Override
  public void addAffectedFiles(Project project, @NotNull VirtualFile... files) {
  }
}
