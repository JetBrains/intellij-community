// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomBuildnumberTask extends AntDomElement implements PropertiesProvider{
  public static final String PROPERTY_NAME = "build.number";

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Override
  public @NotNull Iterator<@NlsSafe String> getNamesIterator() {
    return Collections.singletonList(PROPERTY_NAME).iterator();
  }

  @Override
  public @Nullable @NlsSafe String getPropertyValue(@NlsSafe String propertyName) {
    return PROPERTY_NAME.equals(propertyName)? "" : null;
  }

  @Override
  public PsiElement getNavigationElement(@NlsSafe String propertyName) {
    return PROPERTY_NAME.equals(propertyName)? getXmlElement() : null;
  }
}
