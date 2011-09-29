package org.jetbrains.android.fileTypes;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenderscriptFileType implements FileType {
  public static final String DEFAULT_EXTENSION = "rs";
  public static final AndroidRenderscriptFileType INSTANCE = new AndroidRenderscriptFileType();

  private AndroidRenderscriptFileType() {
  }
  
  @NotNull
  @Override
  public String getName() {
    return "Android RenderScript";
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.renderscript.file.type.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return PlainTextFileType.INSTANCE.getIcon();
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte[] content) {
    return null;
  }
}
