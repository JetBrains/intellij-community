/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.openapi.util;

import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;

/**
 * @author mike
 */
public class JDOMBuilder {
  private JDOMBuilder() {
  }

  public static Document document(Element rootElement) {
    return new Document(rootElement);
  }

  public static Element tag(String name, Content...content) {
    final Element element = new Element(name);
    for (Content c : content) {
      if (c instanceof AttrContent) {
        AttrContent attrContent = (AttrContent)c;
        element.setAttribute(attrContent.myName, attrContent.myValue);
      }
      else {
        element.addContent(c);
      }
    }

    return element;
  }

  public static Content attr(final String name, final String value) {
    return new AttrContent(name, value);
  }

  private static class AttrContent extends Content {
    private final String myName;
    private final String myValue;

    public AttrContent(final String name, final String value) {
      myName = name;
      myValue = value;
    }

    @Override
    public String getValue() {
      throw new UnsupportedOperationException();
    }
  }
}
