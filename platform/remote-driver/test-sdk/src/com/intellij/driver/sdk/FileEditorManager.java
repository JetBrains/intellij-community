package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.NotNull;

@Remote("com.intellij.openapi.fileEditor.FileEditorManager")
public interface FileEditorManager {
  FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor, boolean searchForOpen);
}
