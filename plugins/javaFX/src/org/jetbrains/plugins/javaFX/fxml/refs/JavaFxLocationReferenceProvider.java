// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class JavaFxLocationReferenceProvider extends PsiReferenceProvider {
  private final boolean mySupportCommaInValue;
  private final Set<String> myAcceptedFileTypes;

  JavaFxLocationReferenceProvider() {
    this(false);
  }

  JavaFxLocationReferenceProvider(boolean supportCommaInValue, String... acceptedFileExtensions) {
    mySupportCommaInValue = supportCommaInValue;
    myAcceptedFileTypes = ContainerUtil.newHashSet(acceptedFileExtensions);
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
    final String value = ((XmlAttributeValue)element).getValue();
    if (mySupportCommaInValue && value.contains(",")) {
      int startIdx = 0;
      List<PsiReference> refs = new ArrayList<>();
      while (true) {
        int endIdx = value.indexOf(',', startIdx);
        final String item = endIdx >= 0 ? value.substring(startIdx, endIdx) : value.substring(startIdx);
        Collections.addAll(refs, collectRefs(element, item, startIdx + 1));
        if (endIdx < 0) {
          break;
        }
        startIdx = endIdx + 1;
      }
      return refs.toArray(PsiReference.EMPTY_ARRAY);
    }
    else {
      return collectRefs(element, value, 1);
    }
  }

  private PsiReference[] collectRefs(@NotNull PsiElement element, String value, int startInElement) {
    final int atSignIndex = value.indexOf('@');
    if (atSignIndex >= 0 && (atSignIndex == 0 || StringUtil.trimLeading(value).startsWith("@"))) {
      value = value.substring(atSignIndex + 1);
      startInElement += atSignIndex + 1;
    }
    final FileReferenceSet set = new FileReferenceSet(value, element, startInElement, null, true) {
      @Override
      protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
        return item -> {
          if (item instanceof PsiDirectory) return true;
          final VirtualFile virtualFile = item.getVirtualFile();
          if (virtualFile == null) return false;
          return myAcceptedFileTypes.contains(virtualFile.getExtension());
        };
      }
    };
    if (value.startsWith("/")) {
      set.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, FileReferenceSet.ABSOLUTE_TOP_LEVEL);
    }
    return set.getAllReferences();
  }
}
