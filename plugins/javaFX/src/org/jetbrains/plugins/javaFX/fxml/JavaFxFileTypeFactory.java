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
  public static final String FXML_EXTENSION = "fxml";

  public static boolean isFxml(PsiFile file) {
    final VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(FXML_EXTENSION);
    return virtualFile.getFileType() == fileType && FXML_EXTENSION.equals(virtualFile.getExtension());
  }

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    final FileType fileType = consumer.getStandardFileTypeByName("XML");
    assert fileType != null;
    consumer.consume(fileType, FXML_EXTENSION);
  }
}
