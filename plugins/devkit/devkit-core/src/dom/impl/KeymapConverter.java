// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KeymapConverter extends ResolvingConverter<XmlFile> {

  @Override
  public @Nullable XmlFile fromString(@Nullable String s, @NotNull ConvertContext context) {
    if (StringUtil.isEmpty(s)) return null;

    return ContainerUtil.find(getKeymapFiles(context), file -> s.equals(getKeymapName(file)));
  }

  @Override
  public @Nullable String toString(@Nullable XmlFile file, @NotNull ConvertContext context) {
    return file != null ? getKeymapName(file) : null;
  }

  @Override
  public @NotNull Collection<? extends XmlFile> getVariants(@NotNull ConvertContext context) {
    return getKeymapFiles(context);
  }

  @Override
  public @Nullable LookupElement createLookupElement(XmlFile file) {
    return LookupElementBuilder.create(file, getKeymapName(file));
  }

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.keymap.cannot.resolve", s);
  }

  private static String getKeymapName(XmlFile file) {
    return FileUtilRt.getNameWithoutExtension(file.getName());
  }

  private static @NotNull @Unmodifiable List<XmlFile> getKeymapFiles(ConvertContext context) {
    final PsiPackage keymapsPackage = JavaPsiFacade.getInstance(context.getProject()).findPackage("keymaps");
    if (keymapsPackage == null) {
      return Collections.emptyList();
    }

    final PsiFile[] files = keymapsPackage.getFiles(context.getFile().getResolveScope());
    return ContainerUtil.findAll(files, XmlFile.class);
  }
}