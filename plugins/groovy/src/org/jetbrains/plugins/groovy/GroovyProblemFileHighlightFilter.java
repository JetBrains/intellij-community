package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

/**
* @author yole
*/
public class GroovyProblemFileHighlightFilter implements Condition<VirtualFile> {
  public boolean value(VirtualFile virtualFile) {
    return FileTypeManager.getInstance().getFileTypeByFile(virtualFile) == GroovyFileType.GROOVY_FILE_TYPE;
  }
}
