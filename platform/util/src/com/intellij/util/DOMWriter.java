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

package com.intellij.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.io.PrintWriter;

class DOMWriter {
  private final PrintWriter myOut;
  private final boolean myCanonical;
  @NonNls private static final String XML_PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
  @NonNls private static final String DOCTYPE = "<!DOCTYPE ";
  @NonNls private static final String PUBLIC = " PUBLIC '";
  @NonNls private static final String SYSTEM = " SYSTEM '";
  @NonNls private static final String QUOT = "&quot;";
  @NonNls private static final String AMP = "&amp;";
  @NonNls private static final String GT = "&gt;";
  @NonNls private static final String LT = "&lt;";
  @NonNls private static final String CDATA = "<![CDATA[";
  private static final int INDENT = 2;


  public DOMWriter(boolean canonical, PrintWriter printWriter) {
    myCanonical = canonical;
    myOut = printWriter;
  }


  public void write(Node node) {
    _write(node, 0);
    myOut.println(); //JDOM compatiblity
  }

  private void _write(final Node node, int indent) {
    if (node == null) return;

    short type = node.getNodeType();
    switch (type) {
      case Node.DOCUMENT_NODE: {
        Document document = (Document)node;
        myOut.println(XML_PROLOG);
        _write(document.getDoctype(), indent);
        _write(document.getDocumentElement(), indent);
        break;
      }

      case Node.DOCUMENT_TYPE_NODE: {
        DocumentType doctype = (DocumentType)node;
        myOut.print(DOCTYPE);
        myOut.print(doctype.getName());
        String publicId = doctype.getPublicId();
        String systemId = doctype.getSystemId();
        if (publicId != null) {
          myOut.print(PUBLIC);
          myOut.print(publicId);
          myOut.print("' '");
          myOut.print(systemId);
          myOut.print('\'');
        }
        else {
          myOut.print(SYSTEM);
          myOut.print(systemId);
          myOut.print('\'');
        }
        String internalSubset = doctype.getInternalSubset();
        if (internalSubset != null) {
          myOut.println(" [");
          myOut.print(internalSubset);
          myOut.print(']');
        }
        myOut.println('>');
        break;
      }

      case Node.ELEMENT_NODE: {
        indent(indent);

        myOut.print('<');
        myOut.print(node.getNodeName());

        final NamedNodeMap attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
          Attr attr = (Attr)attributes.item(i);
          myOut.print(' ');
          myOut.print(attr.getNodeName());
          myOut.print("=\"");
          normalizeAndPrint(attr.getNodeValue());
          myOut.print('"');
        }

        Node child = node.getFirstChild();

        if (child == null) {
          myOut.println(" />");

        }
        else {
          myOut.print('>');

          boolean firstChild = true;
          boolean indentNeeded = false;
          while (child != null) {
            if (child instanceof Element) {
              if (firstChild) {
                myOut.println();
                indentNeeded = true;
              }

              firstChild = false;
            }
            else if (!child.getNodeValue().trim().isEmpty()){
              firstChild = false;
            }

            _write(child, indent + INDENT);
            child = child.getNextSibling();
          }

          if (indentNeeded) {
            indent(indent);
          }
          
          myOut.print("</");
          myOut.print(node.getNodeName());
          myOut.println('>');
          break;
        }

      }

      case Node.ENTITY_REFERENCE_NODE: {
        if (myCanonical) {
          Node child = node.getFirstChild();
          while (child != null) {
            _write(child, indent);
            child = child.getNextSibling();
          }
        }
        else {
          myOut.print('&');
          myOut.print(node.getNodeName());
          myOut.print(';');
        }
        break;
      }

      case Node.CDATA_SECTION_NODE: {
        if (myCanonical) {
          normalizeAndPrint(node.getNodeValue());
        }
        else {
          myOut.print(CDATA);
          myOut.print(node.getNodeValue());
          myOut.print("]]>");
        }
        break;
      }

      case Node.TEXT_NODE: {
        final String nodeValue = node.getNodeValue();
        if (!nodeValue.trim().isEmpty()) {
          normalizeAndPrint(nodeValue.trim());
        }
        break;
      }

      case Node.PROCESSING_INSTRUCTION_NODE: {
        myOut.print("<?");
        myOut.print(node.getNodeName());
        String data = node.getNodeValue();
        if (data != null && !data.isEmpty()) {
          myOut.print(' ');
          myOut.print(data);
        }
        myOut.println("?>");
        break;
      }
    }
  }

  private void indent(final int indent) {
    for (int i = 0; i < indent; i++) {
      myOut.print(' ');
    }
  }

  @Nullable
  private static Attr[] sortAttributes(NamedNodeMap attrs) {
    if (attrs == null) return null;

    int len = attrs.getLength();
    Attr[] array = new Attr[len];
    for (int i = 0; i < len; i++) {
      array[i] = (Attr)attrs.item(i);
    }

    return array;

  }

  private void normalizeAndPrint(String s) {
    if (s == null) return;

    int len = s.length();
    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      normalizeAndPrint(c);
    }

  }

  private void normalizeAndPrint(char c) {
    switch (c) {
      case '<': {
        myOut.print(LT);
        break;
      }
      case '>': {
        myOut.print(GT);
        break;
      }
      case '&': {
        myOut.print(AMP);
        break;
      }
      case '"': {
        myOut.print(QUOT);
        break;
      }
      case '\r':
      case '\n': {
        if (myCanonical) {
          myOut.print("&#");
          myOut.print(Integer.toString(c));
          myOut.print(';');
          break;
        }
      }
      default: {
        myOut.print(c);
      }
    }

  }
}
