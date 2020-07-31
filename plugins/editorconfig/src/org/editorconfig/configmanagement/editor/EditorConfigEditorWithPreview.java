// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

public class EditorConfigEditorWithPreview extends TextEditorWithPreview {
  private final DocumentChangeInactivityDetector myInactivityDetector;
  private final DocumentSaveHandler myHandler = new DocumentSaveHandler();
  private final VirtualFile myFile;
  private final Project     myProject;

  public EditorConfigEditorWithPreview(@NotNull VirtualFile file,
                                       @NotNull Project project,
                                       @NotNull TextEditor editor,
                                       @NotNull FileEditor preview) {
    super(editor, preview);
    myFile = file;
    myProject = project;
    myInactivityDetector = new DocumentChangeInactivityDetector(getEditor().getDocument());
    getEditor().getDocument().addDocumentListener(myInactivityDetector);
    myInactivityDetector.addListener(myHandler);
    myInactivityDetector.start();
    ApplicationManager.getApplication().getMessageBus().connect(this).
      subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
        @Override
        public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
          if (Utils.PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
            FileEditorManager.getInstance(myProject).closeFile(myFile);
          }
        }
      });
  }

  @Override
  public void dispose() {
    myInactivityDetector.stop();
    myInactivityDetector.removeListener(myHandler);
    getEditor().getDocument().removeDocumentListener(myInactivityDetector);
    super.dispose();
  }

  private class DocumentSaveHandler implements DocumentChangeInactivityDetector.InactivityListener {

    @Override
    public void onInactivity() {
      ApplicationManager.getApplication().invokeLater(
        () -> FileDocumentManager.getInstance().saveDocumentAsIs(getEditor().getDocument()));
    }
  }
}