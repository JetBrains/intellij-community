/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
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

/**
 * User: anna
 */
class JavaFxLocationReferenceProvider extends PsiReferenceProvider {
  private boolean mySupportCommaInValue = false;
  private final Set<FileType> myAcceptedFileTypes;

  JavaFxLocationReferenceProvider() {
    this(false);
  }

  JavaFxLocationReferenceProvider(boolean supportCommaInValue, String... acceptedFileExtensions) {
    mySupportCommaInValue = supportCommaInValue;
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    myAcceptedFileTypes = ContainerUtil.map2Set(acceptedFileExtensions, fileTypeManager::getFileTypeByExtension);
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element,
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
          final FileType fileType = virtualFile.getFileType();
          return myAcceptedFileTypes.contains(fileType);
        };
      }
    };
    if (value.startsWith("/")) {
      set.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, FileReferenceSet.ABSOLUTE_TOP_LEVEL);
    }
    return set.getAllReferences();
  }
}
