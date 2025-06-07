// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.PackageScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.keymap.KeymapXmlRootElement;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KeymapConverter extends ResolvingConverter<KeymapXmlRootElement> {

  @Override
  public @Nullable KeymapXmlRootElement fromString(@Nullable String s, @NotNull ConvertContext context) {
    if (StringUtil.isEmpty(s)) return null;

    return ContainerUtil.find(getKeymaps(context), file -> s.equals(getKeymapName(file)));
  }

  @Override
  public @Nullable String toString(@Nullable KeymapXmlRootElement file, @NotNull ConvertContext context) {
    return file != null ? getKeymapName(file) : null;
  }

  @Override
  public @NotNull Collection<? extends KeymapXmlRootElement> getVariants(@NotNull ConvertContext context) {
    return getKeymaps(context);
  }

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.keymap.cannot.resolve", s);
  }

  private static String getKeymapName(KeymapXmlRootElement keymapXmlRootElement) {
    return keymapXmlRootElement.getName().getStringValue();
  }

  private static @NotNull @Unmodifiable List<KeymapXmlRootElement> getKeymaps(ConvertContext context) {
    final PsiPackage keymapsPackage = JavaPsiFacade.getInstance(context.getProject()).findPackage("keymaps");
    if (keymapsPackage == null) {
      return Collections.emptyList();
    }

    List<DomFileElement<KeymapXmlRootElement>> domFileElements =
      DomService.getInstance().getFileElements(KeymapXmlRootElement.class,
                                               context.getProject(),
                                               PackageScope.packageScope(keymapsPackage, false));
    return ContainerUtil.map(domFileElements, DomFileElement::getRootElement);
  }
}