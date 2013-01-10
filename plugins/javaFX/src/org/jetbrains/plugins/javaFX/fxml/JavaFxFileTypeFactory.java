package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/8/13
 */
public class JavaFxFileTypeFactory extends FileTypeFactory {
  public static boolean isFxml(PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("fxml");
      return virtualFile.getFileType() == fileType;
    }
    return false;
  }

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    final FileType fileType = consumer.getStandardFileTypeByName("XML");
    assert fileType != null;
    consumer.consume(fileType, "fxml");
  }
}
