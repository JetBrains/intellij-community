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

  @NotNull public List<Converter> getConverters(@NotNull GenericDomValue domElement) {
    final String raw = domElement.getRawText();
    if (raw != null) {
      if (raw.contains("${") || raw.contains(File.separator) || (File.separatorChar != '/' && raw.contains("/"))) {
        return Collections.singletonList(new AntPathConverter());
      }
    }
    return Collections.emptyList();
  }

  public Converter getConverter(@NotNull GenericDomValue domElement) {
    final List<Converter> converterList = getConverters(domElement);
    return converterList.isEmpty()? null : converterList.get(0);
  }
}
