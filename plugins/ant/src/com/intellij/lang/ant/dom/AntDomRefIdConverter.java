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

import com.intellij.lang.ant.AntSupport;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomRefIdConverter extends ResolvingConverter<XmlAttributeValue>{
  @NotNull
  public Collection<? extends XmlAttributeValue> getVariants(ConvertContext context) {
    // todo: should we add imported files support?
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null) {
      final List<XmlAttributeValue> variants = new ArrayList<XmlAttributeValue>();
      element.getAntProject().accept(new DomElementVisitor() {
        public void visitDomElement(DomElement element) {
        }
        public void visitAntDomElement(AntDomElement element) {
          final GenericAttributeValue<String> idValue = element.getId();
          if (idValue != null) {
            final XmlAttributeValue xmlAttribValue = idValue.getXmlAttributeValue();
            if (xmlAttribValue != null) {
              variants.add(xmlAttribValue);
            }
          }
          for (AntDomElement child : element.getAntChildren()) {
            child.accept(this);
          }
        }
      });
      return variants;
    }
    return Collections.emptyList();
  }

  @Nullable
  public XmlAttributeValue fromString(@Nullable @NonNls String s, ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null) {
      final AntDomElement resolved = findElementById(element.getAntProject(), s);
      if (resolved == null)  {
        return null;
      }

      return resolved.getId().getXmlAttributeValue();
    }
    return null;
  }

  @Nullable
  private static AntDomElement findElementById(AntDomElement from, final String id) {
    final GenericAttributeValue<String> idValue = from.getId();
    if (idValue != null) {
      if (id.equals(idValue.getStringValue())) {
        return from;
      }
    }
    for (AntDomElement child : from.getAntChildren()) {
      final AntDomElement result = findElementById(child, id);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public String toString(@Nullable XmlAttributeValue attribValue, ConvertContext context) {
    if (attribValue == null) {
      return null;
    }
    return attribValue.getValue();
  }
}
