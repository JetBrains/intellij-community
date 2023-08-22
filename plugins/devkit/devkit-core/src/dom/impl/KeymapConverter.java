// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KeymapConverter extends ResolvingConverter<XmlFile> {

  @Nullable
  @Override
  public XmlFile fromString(@Nullable String s, ConvertContext context) {
    if (StringUtil.isEmpty(s)) return null;

    return ContainerUtil.find(getKeymapFiles(context), file -> s.equals(getKeymapName(file)));
  }

  @Nullable
  @Override
  public String toString(@Nullable XmlFile file, ConvertContext context) {
    return file != null ? getKeymapName(file) : null;
  }

  @NotNull
  @Override
  public Collection<? extends XmlFile> getVariants(ConvertContext context) {
    return getKeymapFiles(context);
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(XmlFile file) {
    return LookupElementBuilder.create(getKeymapName(file));
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.keymap.cannot.resolve", s);
  }

  private static String getKeymapName(XmlFile file) {
    return FileUtilRt.getNameWithoutExtension(file.getName());
  }

  @NotNull
  private static List<XmlFile> getKeymapFiles(ConvertContext context) {
    final PsiPackage keymapsPackage = JavaPsiFacade.getInstance(context.getProject()).findPackage("keymaps");
    if (keymapsPackage == null) {
      return Collections.emptyList();
    }

    final PsiFile[] files = keymapsPackage.getFiles(context.getFile().getResolveScope());
    return ContainerUtil.findAll(files, XmlFile.class);
  }
}