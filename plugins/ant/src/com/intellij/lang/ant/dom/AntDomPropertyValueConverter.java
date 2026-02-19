// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.WrappingConverter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomPropertyValueConverter extends WrappingConverter{

  @Override
  public @NotNull List<Converter> getConverters(@NotNull GenericDomValue domElement) {
    final String raw = domElement.getRawText();
    if (raw != null) {
      if (raw.contains("${") || raw.contains(File.separator) || (File.separatorChar != '/' && raw.contains("/"))) {
        return Collections.singletonList(new AntPathConverter());
      }
    }
    return Collections.emptyList();
  }

  @Override
  public Converter getConverter(@NotNull GenericDomValue domElement) {
    final List<Converter> converterList = getConverters(domElement);
    return converterList.isEmpty()? null : converterList.get(0);
  }
}
