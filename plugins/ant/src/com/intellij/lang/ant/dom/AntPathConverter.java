/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2010
 */
public class AntPathConverter extends Converter<PsiFileSystemItem> implements CustomReferenceConverter<PsiFileSystemItem>{
  @Override
  public PsiFileSystemItem fromString(@Nullable @NonNls String s, ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    final String path = attribValue.getStringValue();
    if (path == null) {
      return null;
    }
    File file = new File(path);
    if (!file.isAbsolute()) {
      final AntDomProject antProject = getEffectiveAntProject(attribValue);
      if (antProject == null) {
        return null;
      }
      file = new File(getPathResolveRoot(antProject), path);
    }
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
    if (vFile == null) {
      return null;
    }
    final PsiManager psiManager = context.getPsiManager();

    return vFile.isDirectory()? psiManager.findDirectory(vFile) : psiManager.findFile(vFile);
  }

  protected AntDomProject getEffectiveAntProject(GenericAttributeValue attribValue) {
    // todo: get context (including) project if configured 
    return attribValue.getParentOfType(AntDomProject.class, false);
  }

  @Nullable
  protected String getPathResolveRoot(AntDomProject antProject) {
    return antProject.getProjectBasedirPath();
  }

  @Override
  public String toString(@Nullable PsiFileSystemItem file, ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }


  @NotNull
  public PsiReference[] createReferences(GenericDomValue<PsiFileSystemItem> genericDomValue, PsiElement element, ConvertContext context) {
    if (genericDomValue instanceof GenericAttributeValue) {
      final GenericAttributeValue attrib = (GenericAttributeValue)genericDomValue;
      if (attrib.getRawText() != null) {
        final AntDomFileReferenceSet refSet = new AntDomFileReferenceSet(attrib);
        return refSet.getAllReferences();
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

}
