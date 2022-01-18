package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface BreadcrumbsInitListener {
  Topic<BreadcrumbsInitListener> TOPIC = new Topic<>(BreadcrumbsInitListener.class);

  void breadcrumbsInitialized(@NotNull BreadcrumbsPanel wrapper, @NotNull FileEditor fileEditor, @NotNull FileEditorManager manager);
}
