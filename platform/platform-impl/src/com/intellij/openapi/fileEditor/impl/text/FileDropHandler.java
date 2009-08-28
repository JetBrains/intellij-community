package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;

/**
* @author yole
*/
public class FileDropHandler implements EditorDropHandler {
  public boolean canHandleDrop(DataFlavor[] transferFlavors) {
    for (DataFlavor transferFlavor : transferFlavors) {
      if (transferFlavor.equals(DataFlavor.javaFileListFlavor)) {
        return true;
      }
    }
    return false;
  }

  public void handleDrop(Transferable t, final Project project) {
    if (project == null) {
      return;
    }
    java.util.List<File> fileList;
    try {
      //noinspection unchecked
      fileList = (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
    }
    catch (Exception ex) {
      return;
    }
    if (fileList != null) {
      for (File file : fileList) {
        final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (vFile != null) {
          new OpenFileDescriptor(project, vFile).navigate(true);
        }
      }
    }
  }
}
