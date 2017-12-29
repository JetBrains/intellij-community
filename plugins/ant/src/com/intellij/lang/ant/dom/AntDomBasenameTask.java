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

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomBasenameTask extends AntDomPropertyDefiningTask {
  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Attribute("suffix")
  public abstract GenericAttributeValue<String> getSuffix();

  protected String calcPropertyValue(String propertyName) {
    final PsiFileSystemItem item = getFile().getValue();
    if (item != null) {
      final String name = item.getName();
      final String suffix = getSuffix().getStringValue();
      return suffix != null && name.endsWith(suffix)? name.substring(0, name.length() - suffix.length()) : name;
    }
    return super.calcPropertyValue(propertyName);
  }
}
