package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
* User: anna
* Date: 4/3/13
*/
public class JavaFxFileReferenceProvider extends PsiReferenceProvider {
  
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
    final Object value = ((PsiLiteralExpression)element).getValue();
    final PsiDirectory directory = element.getContainingFile().getOriginalFile().getParent();
    if (!(value instanceof String) || directory == null) return PsiReference.EMPTY_ARRAY;
    final boolean startsWithSlash = ((String)value).startsWith("/");
    final VirtualFileSystem fs = directory.getVirtualFile().getFileSystem();
    final FileReferenceSet fileReferenceSet = new FileReferenceSet((String)value, element, 1, null, ((NewVirtualFileSystem)fs).isCaseSensitive()) {
      @NotNull
      @Override
      public Collection<PsiFileSystemItem> getDefaultContexts() {
        if (startsWithSlash || !directory.isValid()) {
          return super.getDefaultContexts();
        }
        return Collections.<PsiFileSystemItem>singletonList(directory);
      }
    };
    if (startsWithSlash) {
      fileReferenceSet.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, FileReferenceSet.ABSOLUTE_TOP_LEVEL);
    }
    return fileReferenceSet.getAllReferences();
  }
}
