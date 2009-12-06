/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;

import java.text.MessageFormat;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMCompare {
  private JDOMCompare() {
  }

  public static String diffDocuments(Document documentExpected, Document documentActual){
    return diffElements(documentExpected.getRootElement(), documentActual.getRootElement(), "", 0 );
  }

  public static String diffElements(Element elementExpected, Element elementActual, String pathPrefix, int order) {
    final String expectedTag = elementExpected.getName();
    pathPrefix = MessageFormat.format("{0}/{1}[{2}]", pathPrefix, expectedTag, order);

    final String actualTag = elementActual.getName();
    if ( ! elementExpected.getName().equals(actualTag) ) {
      return MessageFormat.format("Tag mismatch at {0}: expected={1}, actual={2}", pathPrefix, expectedTag, actualTag);
    }

    final String expectedText = elementExpected.getText().trim();
    final String actualText = elementActual.getText().trim();
    if (expectedText != null) {
      if (actualText == null) {
        return MessageFormat.format("Text content missing at {0}: expected={1}", pathPrefix, expectedText);
      } else if (!expectedText.equals(actualText)) {
        return MessageFormat.format("Text content mismatch at {0}: expected={1}, actual={2}", pathPrefix, expectedText, actualText);
      }
    } else if (actualText != null) {
        return MessageFormat.format("Text content unexpected at {0}: actual={1}", pathPrefix, actualText);
    }

    String result = diffAttributes(elementExpected, elementActual, pathPrefix, "missing");
    if ( result != null ) return result;

    result = diffAttributes(elementActual, elementExpected, pathPrefix, "unexpected");
    if ( result != null ) return result;

    List childrenExpected = elementExpected.getChildren();
    List childrenActual = elementActual.getChildren();

    for (int i = 0; i != childrenExpected.size(); i++) {
      final Element expectedChild = (Element) childrenExpected.get(i);
      if ( i >= childrenActual.size () ) {
        return MessageFormat.format("Too few children at {0}, expected={1}, actual={2}, first missing tag={3}",
                pathPrefix, childrenExpected.size(), childrenActual.size(), expectedChild.getName());
      }
      result = diffElements(expectedChild, (Element) childrenActual.get(i), pathPrefix, i);
      if ( result != null ) return result;
    }

    if ( childrenExpected.size() != childrenActual.size()) {
      return MessageFormat.format("Too many children at {0}, expected={1}, actual={2}, first unexpected tag={3}",
              pathPrefix, childrenExpected.size(), childrenActual.size(), ((Element) childrenActual.get(childrenExpected.size())).getName() );
    }

    return null;
  }

  public static String diffAttributes(Element element1, Element element2, String pathPrefix, String mode) {
    for (Object o : element1.getAttributes()) {
      final Attribute attr = (Attribute) o;
      final String name = attr.getName();
      final String value1 = attr.getValue();
      final String value2 = element2.getAttributeValue(name);
      if ( value2 == null ) {
        return MessageFormat.format("Attribute {2} at {0}/@{1}", pathPrefix, name, mode);
      }
      if ( ! value1.equals(value2)){
       return MessageFormat.format("Attribute value mismatch at {0}/@{1}, expected={2}, actual={3}", pathPrefix, name, value1, value2);
      }
    }
    return null;
  }
}