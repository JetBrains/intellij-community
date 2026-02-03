// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
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
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AntDomFileReferenceSet extends FileReferenceSet {
  private final GenericAttributeValue<?> myValue;

  public AntDomFileReferenceSet(final GenericAttributeValue<?> attribValue, boolean validateFileRefs) {
    this(attribValue, attribValue.getRawText(), 0, validateFileRefs);
  }

  public AntDomFileReferenceSet(final GenericAttributeValue<?> attribValue, final String pathSubstring, int beginOffset, boolean validateFileRefs) {
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

  public GenericAttributeValue<?> getAttributeValue() {
    return myValue;
  }

  private static String cutTrailingSlash(String path) {
    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }
    return path;
  }

  @Override
  protected boolean isSoft() {
    return true;
  }

  @Override
  public FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new AntDomFileReference(this, range, index, text);
  }

  @Override
  public @NotNull XmlAttributeValue getElement() {
    return (XmlAttributeValue)super.getElement();
  }

  @Override
  public @NlsSafe @NotNull String getPathString() {
    return myValue.getStringValue();
  }

  @Override
  public boolean isAbsolutePathReference() {
    if (super.isAbsolutePathReference()) {
      return true;
    }

    String path = getPathString();
    return FileUtil.isAbsolute(path);
  }

  @Override
  public @NotNull Collection<PsiFileSystemItem> computeDefaultContexts() {
    final AntDomElement element = myValue.getParentOfType(AntDomElement.class, false);
    final AntDomProject containingProject = element != null? element.getAntProject() : null;

    if (containingProject != null) {
      if (isAbsolutePathReference()) {
        return toFileSystemItems(ManagingFS.getInstance().getLocalRoots());
      }
      else {
        VirtualFile root = null;

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
          if (basedir != null) {
            root = LocalFileSystem.getInstance().findFileByPath(basedir);
          }
        }

        if (root != null) {
          return toFileSystemItems(root);
        }
      }
    }

    return super.computeDefaultContexts();
  }
}
