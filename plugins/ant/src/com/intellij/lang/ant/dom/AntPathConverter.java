// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
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
 */
public class AntPathConverter extends Converter<PsiFileSystemItem> implements CustomReferenceConverter<PsiFileSystemItem>{

  private final boolean myShouldValidateRefs;

  public AntPathConverter() {
    this(false);
  }

  protected AntPathConverter(boolean validateRefs) {
    myShouldValidateRefs = validateRefs;
  }

  @Override
  public PsiFileSystemItem fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    String path = attribValue.getStringValue();
    if (path == null) {
      path = getAttributeDefaultValue(context, attribValue);
    }
    if (path == null) {
      return null;
    }
    File file = new File(path);
    if (!file.isAbsolute()) {
      final AntDomProject antProject = getEffectiveAntProject(attribValue);
      if (antProject == null) {
        return null;
      }
      file = new File(getPathResolveRoot(context, antProject), path);
    }
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
    if (vFile == null) {
      return null;
    }
    final PsiManager psiManager = context.getPsiManager();

    return vFile.isDirectory()? psiManager.findDirectory(vFile) : psiManager.findFile(vFile);
  }

  protected AntDomProject getEffectiveAntProject(GenericAttributeValue attribValue) {
    AntDomProject project = attribValue.getParentOfType(AntDomProject.class, false);
    if (project != null) {
      project = project.getContextAntProject();
    }
    return project;
  }

  protected @Nullable @NlsSafe String getPathResolveRoot(ConvertContext context, AntDomProject antProject) {
    return antProject.getProjectBasedirPath();
  }

  protected @Nullable @NlsSafe String getAttributeDefaultValue(ConvertContext context, GenericAttributeValue attribValue) {
    return null;
  }

  @Override
  public String toString(@Nullable PsiFileSystemItem file, @NotNull ConvertContext context) {
    final GenericAttributeValue attribValue = context.getInvocationElement().getParentOfType(GenericAttributeValue.class, false);
    if (attribValue == null) {
      return null;
    }
    return attribValue.getRawText();
  }


  @Override
  public PsiReference @NotNull [] createReferences(GenericDomValue<PsiFileSystemItem> genericDomValue, PsiElement element, ConvertContext context) {
    if (genericDomValue instanceof GenericAttributeValue attrib) {
      if (attrib.getRawText() != null) {
        final AntDomFileReferenceSet refSet = new AntDomFileReferenceSet(attrib, myShouldValidateRefs);
        return refSet.getAllReferences();
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

}
