package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;

/**
 * @author Vladimir Kondratyev
 */
public final class GotoHomeAction extends FileChooserAction {
  protected void actionPerformed(final FileSystemTree fileSystemTree, AnActionEvent e) {
    final VirtualFile homeDirectory = getHomeDirectory();
    fileSystemTree.select(homeDirectory, new Runnable() {
      public void run() {
        fileSystemTree.expand(homeDirectory, null);
      }
    });
  }

  private static VirtualFile getHomeDirectory(){
    return LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if(!presentation.isEnabled()){
      return;
    }
    final VirtualFile homeDirectory = getHomeDirectory();
    presentation.setEnabled(homeDirectory != null && (fileSystemTree).isUnderRoots(homeDirectory));
  }
}
