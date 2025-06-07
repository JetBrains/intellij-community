// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.ForcedAntFileAttribute;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public final class AntDomFileDescription extends AntFileDescription<AntDomProject> {
  private static final String ROOT_TAG_NAME = "project";

  public AntDomFileDescription() {
    super(AntDomProject.class, ROOT_TAG_NAME);
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return super.isMyFile(file, module) && isAntFile(file);
  }

  @Override
  public @Nullable Icon getFileIcon(@Iconable.IconFlags int flags) {
    return AntIcons.AntBuildXml;
  }

  public static boolean isAntFile(final XmlFile xmlFile) {
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      final VirtualFile vFile = xmlFile.getOriginalFile().getVirtualFile();
      if (tag != null && ROOT_TAG_NAME.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue("name") != null && tag.getAttributeValue("default") != null
            && vFile != null && ForcedAntFileAttribute.mayBeAntFile(vFile)) {
          return true;
        }
      }
      if (vFile != null && ForcedAntFileAttribute.isAntFile(vFile)) {
        return true;
      }
    }
    return false;
  }

}
