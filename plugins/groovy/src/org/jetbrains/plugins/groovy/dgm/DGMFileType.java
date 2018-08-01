// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DGMFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
  public static final DGMFileType INSTANCE = new DGMFileType();
  private DGMFileType() {
    super(PropertiesLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "DGM File Type";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Groovy extension module descriptor file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return PropertiesFileType.INSTANCE.getIcon();
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent != null && Comparing.equal("services", parent.getNameSequence())) {
      final VirtualFile gParent = parent.getParent();
      if (gParent != null && Comparing.equal("META-INF", gParent.getNameSequence())) {
        final String fileName = file.getName();
        return fileName.equals(DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE);
      }
    }
    return false;
  }
}
