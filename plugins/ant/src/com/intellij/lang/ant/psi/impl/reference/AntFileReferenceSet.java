/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class AntFileReferenceSet extends FileReferenceSet {

  private final XmlAttributeValue myValue;

  public AntFileReferenceSet(final AntStructuredElement element, final XmlAttributeValue value, final PsiReferenceProvider provider) {
    super(cutTrailingSlash(FileUtil.toSystemIndependentName(StringUtil.stripQuotesAroundValue(value.getText()))), element,
          value.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1, provider, SystemInfo.isFileSystemCaseSensitive);
    myValue = value;
  }

  private static String cutTrailingSlash(String path) {
    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }
    return path;
  }
  
  protected boolean isSoft() {
    final AntStructuredElement element = getElement();
    if (!(element instanceof AntImport   || 
             element instanceof AntTypeDef  || 
             element instanceof AntProperty || 
             element instanceof AntAnt)) {
      return true;
    }
    if (element instanceof AntProperty) {
      return ((AntProperty)element).getFileName() != null;
    }
    return false;
  }

  public AntFileReference createFileReference(final TextRange range, final int index, final String text) {
    return new AntFileReference(this, range, index, text);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  @Nullable
  public String getPathString() {
    return getElement().computeAttributeValue(super.getPathString());
  }

  public boolean isAbsolutePathReference() {
    if (super.isAbsolutePathReference()) {
      return true;
    }
    final String pathString = getPathString();
    if (SystemInfo.isWindows && pathString.length() == 2 && Character.isLetter(pathString.charAt(0)) && pathString.charAt(1) == ':') {
      return true;
    }
    return FileUtil.isAbsolute(pathString);
  }
  // todo: correct context for "output" attribute file reference of the "ant" task
  @NotNull
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final AntStructuredElement element = getElement();
    final AntFile antFile = element.getAntFile();
    if (antFile != null) {
      VirtualFile vFile = antFile.getContainingPath();
      if (isAbsolutePathReference()) {
        if (SystemInfo.isWindows) {
          vFile = ManagingFS.getInstance().findRoot("", LocalFileSystem.getInstance());
        }    
        else {
          vFile = LocalFileSystem.getInstance().findFileByPath("/");
        }
      }
      else {
        if (!(element instanceof AntImport)) {
          final String basedir = element.computeAttributeValue("${basedir}");
          assert basedir != null;
          vFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(basedir));
        } 
      }

      if (vFile != null) {
        final PsiDirectory directory = antFile.getViewProvider().getManager().findDirectory(vFile);
        if (directory != null) {
          return Collections.<PsiFileSystemItem>singleton(directory);
        }
      }
    }
    return super.computeDefaultContexts();
  }

  XmlAttributeValue getManipulatorElement() {
    return myValue;
  }
}
