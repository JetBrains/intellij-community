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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 */
class JavaFxLocationReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element,
                                               @NotNull ProcessingContext context) {
    final String value = ((XmlAttributeValue)element).getValue();
    final String relativePathToResource = value.substring(1);

    final VirtualFile virtualFile = element.getContainingFile().getOriginalFile().getVirtualFile();
    if (virtualFile != null) {
      final VirtualFile parent = virtualFile.getParent();
      final VirtualFile resourceFile = parent != null ? parent.findFileByRelativePath(relativePathToResource) : null;
      final PsiReferenceBase.Immediate<XmlAttributeValue> ref =
        new PsiReferenceBase.Immediate<XmlAttributeValue>((XmlAttributeValue)element, resourceFile != null ? element.getManager().findFile(resourceFile) : null) {
          @NotNull
          @Override
          public Object[] getVariants() {
            if (parent != null) {  //todo multilevel completion, filter by resources
              final PsiDirectory psiDirectory = element.getManager().findDirectory(parent);
              if (psiDirectory != null) {
                final PsiElement[] children = psiDirectory.getChildren();
                final List<String> paths = new ArrayList<String>();
                for (PsiElement child : children) {
                  if (child instanceof PsiFileSystemItem) {
                    paths.add("@" + ((PsiFileSystemItem)child).getName());
                  }
                }
                return ArrayUtil.toStringArray(paths);
              }
            }
            return super.getVariants();
          }
        };
      return new PsiReference[]{ref};
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
