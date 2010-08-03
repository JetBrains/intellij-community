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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public abstract class AntDomDirname extends AntDomNamedElement implements PropertiesProvider{
  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Attribute("property")
  public abstract GenericAttributeValue<String> getPropertyName();

  @NotNull
  public final Iterator<String> getNamesIterator() {
    final String propName = getPropertyName().getStringValue();
    return propName != null? Collections.singletonList(propName).iterator() : Collections.<String>emptyList().iterator();
  }

  public PsiElement getNavigationElement(final String propertyName) {
    final DomTarget domTarget = DomTarget.getTarget(this, getPropertyName());
    if (domTarget != null) {
      final PsiElement psi = PomService.convertToPsi(domTarget);
      if (psi != null) {
        return psi;
      }
    }
    return getXmlElement();
  }

  @Nullable
  public final String getPropertyValue(String propertyName) {
    final PsiFileSystemItem fsItem = getFile().getValue();
    if (fsItem != null) {
      final PsiFileSystemItem parent = fsItem.getParent();
      if (parent != null) {
        final VirtualFile vFile = parent.getVirtualFile();
        if (vFile != null) {
          return FileUtil.toSystemDependentName(vFile.getPath());
        }
      }
    }
    return null;
  }

}
