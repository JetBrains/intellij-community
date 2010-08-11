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
import com.intellij.util.xml.NameValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 10, 2010
 */
public abstract class AntDomExecTask extends AntDomElement implements PropertiesProvider{

  @Attribute("outputproperty")
  @NameValue
  public abstract GenericAttributeValue<String> getOutputPropertyName();

  @Attribute("errorproperty")
  @NameValue
  public abstract GenericAttributeValue<String> getErrorPropertyName();

  @Attribute("resultproperty")
  @NameValue
  public abstract GenericAttributeValue<String> getResultPropertyName();

  @NotNull 
  public final Iterator<String> getNamesIterator() {
    final List<GenericAttributeValue<String>> attribs = getPropertyDefiningAttributes();
    final List<String> result = new ArrayList<String>(attribs.size());
    for (GenericAttributeValue<String> attribValue : attribs) {
      final String name = attribValue.getStringValue();
      if (name != null && name.length() > 0) {
        result.add(name);
      }
    }
    return result.iterator();
  }

  public final PsiElement getNavigationElement(String propertyName) {
    for (GenericAttributeValue<String> value : getPropertyDefiningAttributes()) {
      if (!propertyName.equals(value.getStringValue())) {
        continue;
      }
      final DomTarget domTarget = DomTarget.getTarget(this, value);
      return domTarget != null? PomService.convertToPsi(domTarget) : null;
    }
    return null;
  }
  
  public String getPropertyValue(final String propertyName) {
    for (GenericAttributeValue<String> value : getPropertyDefiningAttributes()) {
      if (propertyName.equals(value.getStringValue())) {
        return calcPropertyValue(propertyName);
      }
    }
    return null;
  }

  protected String calcPropertyValue(String propName) {
    return "";
  }

  private List<GenericAttributeValue<String>> getPropertyDefiningAttributes() {
    return Arrays.asList(getOutputPropertyName(), getResultPropertyName(), getErrorPropertyName());
  }
}
