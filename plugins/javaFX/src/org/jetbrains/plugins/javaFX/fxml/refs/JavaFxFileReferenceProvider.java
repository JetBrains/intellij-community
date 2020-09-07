// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class JavaFxFileReferenceProvider extends PsiReferenceProvider {

  private final String myAcceptedExtension;

  public JavaFxFileReferenceProvider(String acceptedExtension) {
    myAcceptedExtension = acceptedExtension;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
    final Object value = ((PsiLiteralExpression)element).getValue();
    if (!(value instanceof String)) return PsiReference.EMPTY_ARRAY;
    return getReferences(element, preprocessValue((String)value), myAcceptedExtension);
  }

  protected String preprocessValue(String value) {
    return value;
  }

  public static PsiReference[] getReferences(@NotNull PsiElement element, String value, final String acceptedExtension) {
    final PsiDirectory directory = element.getContainingFile().getOriginalFile().getParent();
    if (directory == null) return PsiReference.EMPTY_ARRAY;
    final boolean startsWithSlash = value.startsWith("/");
    final FileReferenceSet fileReferenceSet = new FileReferenceSet(value, element, 1, null, directory.getVirtualFile().isCaseSensitive()) {
      @NotNull
      @Override
      public Collection<PsiFileSystemItem> getDefaultContexts() {
        if (startsWithSlash || !directory.isValid()) {
          return super.getDefaultContexts();
        }
        return Collections.singletonList(directory);
      }

      @Override
      protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
        return item -> {
          if (item instanceof PsiDirectory) return true;
          final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(item);
          return virtualFile != null && acceptedExtension.equals(virtualFile.getExtension());
        };
      }
    };
    if (startsWithSlash) {
      fileReferenceSet.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, FileReferenceSet.ABSOLUTE_TOP_LEVEL);
    }
    return fileReferenceSet.getAllReferences();
  }
}
