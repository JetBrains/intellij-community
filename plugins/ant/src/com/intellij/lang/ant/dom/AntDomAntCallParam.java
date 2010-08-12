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

import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 11, 2010
 */
public abstract class AntDomAntCallParam extends AntDomNamedElement implements PropertiesProvider{
  
  @Attribute("value")
  public abstract GenericAttributeValue<String> getValue();
  
  @NotNull 
  public final Iterator<String> getNamesIterator() {
    final String name = getName().getStringValue();
    if (name != null && name.length() > 0) {
      return Collections.<String>singletonList(name).iterator();
    }
    return Collections.<String>emptyList().iterator();
  }

  public final PsiElement getNavigationElement(String propertyName) {
    final DomTarget domTarget = DomTarget.getTarget(this);
    if (domTarget != null) {
      return PomService.convertToPsi(domTarget);
    }
    return null;
  }
  
  public String getPropertyValue(String propertyName) {
    if (!propertyName.equals(getName().getStringValue())) {
      return null;
    }
    return getValue().getStringValue();
  }
}
