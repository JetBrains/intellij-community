package org.jetbrains.android.fileTypes;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidIdlFileImpl extends PsiFileBase {
  private final FileType myFileType;

  public AndroidIdlFileImpl(FileViewProvider viewProvider) {
    //super(AndroidIdlParserDefinition.AIDL_FILE_ELEMENT_TYPE, AndroidIdlParserDefinition.AIDL_TEXT, viewProvider);
    super(viewProvider, AndroidIdlFileType.ourFileType.getLanguage());
    myFileType = viewProvider.getVirtualFile().getFileType();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  /*public void accept(@NotNull final PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }*/
}
