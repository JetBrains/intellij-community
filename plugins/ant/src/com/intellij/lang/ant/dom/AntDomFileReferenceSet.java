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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class AntDomFileReferenceSet extends FileReferenceSet {

  private final GenericAttributeValue myValue;

  public AntDomFileReferenceSet(final GenericAttributeValue attribValue, boolean validateFileRefs) {
    this(attribValue, attribValue.getRawText(), 0, validateFileRefs);
  }

  public AntDomFileReferenceSet(final GenericAttributeValue attribValue, final String pathSubstring, int beginOffset, boolean validateFileRefs) {
    super(cutTrailingSlash(FileUtil.toSystemIndependentName(pathSubstring)),
          attribValue.getXmlAttributeValue(),
          ElementManipulators.getOffsetInElement(attribValue.getXmlAttributeValue()) + beginOffset,
          null,
          SystemInfo.isFileSystemCaseSensitive
    );
    myValue = attribValue;
    for (FileReference reference : getAllReferences()) {
      if (reference instanceof AntDomReference) {
        ((AntDomReference)reference).setShouldBeSkippedByAnnotator(!validateFileRefs);
      }
    }
  }

  public GenericAttributeValue getAttributeValue() {
    return myValue;
  }

  private static String cutTrailingSlash(String path) {
    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }
    return path;
  }
  
  protected boolean isSoft() {
    return true;
  }

  public FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new AntDomFileReference(this, range, index, text);
  }

  @Override
  public XmlAttributeValue getElement() {
    return (XmlAttributeValue)super.getElement();
  }

  @Nullable
  public String getPathString() {
    return myValue.getStringValue();
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

  @NotNull
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final AntDomElement element = myValue.getParentOfType(AntDomElement.class, false);
    final AntDomProject containingProject = element != null? element.getAntProject() : null;
    if (containingProject != null) {
      VirtualFile root = null;
      if (isAbsolutePathReference()) {
        if (SystemInfo.isWindows) {
          root = ManagingFS.getInstance().findRoot("", LocalFileSystem.getInstance());
        }    
        else {
          root = LocalFileSystem.getInstance().findFileByPath("/");
        }
      }
      else {
        
        if (element instanceof AntDomAnt) {
          final PsiFileSystemItem dirValue = ((AntDomAnt)element).getAntFileDir().getValue();
          if (dirValue instanceof PsiDirectory) {
            root = dirValue.getVirtualFile();
          }
        }

        if (root == null) {
          final String basedir;
          if (element instanceof AntDomIncludingDirective) {
            basedir = containingProject.getContainingFileDir();
          }
          else {
            basedir = containingProject.getContextAntProject().getProjectBasedirPath();
          }
          assert basedir != null;
          root = LocalFileSystem.getInstance().findFileByPath(basedir);
        }
      }

      if (root != null) {
        final XmlElement xmlElement = containingProject.getXmlElement();
        if (xmlElement != null) {
          final PsiDirectory directory = xmlElement.getManager().findDirectory(root);
          if (directory != null) {
            return Collections.<PsiFileSystemItem>singleton(directory);
          }
        }
      }
    }
    return super.computeDefaultContexts();
  }
}
