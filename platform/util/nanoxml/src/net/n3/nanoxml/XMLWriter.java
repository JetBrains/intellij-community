/* XMLWriter.java                                                  NanoXML/Java
 *
 * $Revision: 1.4 $
 * $Date: 2002/03/24 11:37:51 $
 * $Name: RELEASE_2_2_1 $
 *
 * This file is part of NanoXML 2 for Java.
 * Copyright (C) 2000-2002 Marc De Scheemaecker, All Rights Reserved.
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the
 * use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software in
 *     a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *
 *  3. This notice may not be removed or altered from any source distribution.
 */

package net.n3.nanoxml;


import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Vector;


/**
 * An XMLWriter writes XML data to a stream.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 * @see IXMLElement
 * @see Writer
 */
public final class XMLWriter {

  /**
   * Where to write the output to.
   */
  private PrintWriter writer;


  /**
   * Creates a new XML writer.
   *
   * @param writer where to write the output to.
   */
  public XMLWriter(Writer writer) {
    if (writer instanceof PrintWriter) {
      this.writer = (PrintWriter)writer;
    }
    else {
      this.writer = new PrintWriter(writer);
    }
  }


  /**
   * Creates a new XML writer.
   *
   * @param stream where to write the output to.
   */
  public XMLWriter(OutputStream stream) {
    this.writer = new PrintWriter(stream, false, StandardCharsets.UTF_8);
  }


  /**
   * Cleans up the object when it's destroyed.
   */
  @Override
  protected void finalize()
    throws Throwable {
    this.writer = null;
    super.finalize();
  }


  /**
   * Writes an XML element.
   *
   * @param xml the non-null XML element to write.
   */
  public void write(IXMLElement xml)
    throws IOException {
    this.write(xml, false, 0, true);
  }


  /**
   * Writes an XML element.
   *
   * @param xml         the non-null XML element to write.
   * @param prettyPrint if spaces need to be inserted to make the output more
   *                    readable
   */
  public void write(IXMLElement xml,
                    boolean prettyPrint)
    throws IOException {
    this.write(xml, prettyPrint, 0, true);
  }


  /**
   * Writes an XML element.
   *
   * @param xml         the non-null XML element to write.
   * @param prettyPrint if spaces need to be inserted to make the output more
   *                    readable
   * @param indent      how many spaces to indent the element.
   */
  public void write(IXMLElement xml,
                    boolean prettyPrint,
                    int indent)
    throws IOException {
    this.write(xml, prettyPrint, indent, true);
  }


  /**
   * Writes an XML element.
   *
   * @param xml         the non-null XML element to write.
   * @param prettyPrint if spaces need to be inserted to make the output more
   *                    readable
   * @param indent      how many spaces to indent the element.
   */
  public void write(IXMLElement xml,
                    boolean prettyPrint,
                    int indent,
                    boolean collapseEmptyElements)
    throws IOException {
    if (prettyPrint) {
      for (int i = 0; i < indent; i++) {
        this.writer.print(' ');
      }
    }

    if (xml.getName() == null) {
      if (xml.getContent() != null) {
        if (prettyPrint) {
          this.writeEncoded(xml.getContent().trim());
          writer.println();
        }
        else {
          this.writeEncoded(xml.getContent());
        }
      }
    }
    else {
      this.writer.print('<');
      this.writer.print(xml.getFullName());
      Vector nsprefixes = new Vector();

      if (xml.getNamespace() != null) {
        if (xml.getName().equals(xml.getFullName())) {
          this.writer.print(" xmlns=\"" + xml.getNamespace() + '"');
        }
        else {
          String prefix = xml.getFullName();
          prefix = prefix.substring(0, prefix.indexOf(':'));
          nsprefixes.addElement(prefix);
          this.writer.print(" xmlns:" + prefix);
          this.writer.print("=\"" + xml.getNamespace() + "\"");
        }
      }

      Enumeration enumeration = xml.enumerateAttributeNames();

      while (enumeration.hasMoreElements()) {
        String key = (String)enumeration.nextElement();
        int index = key.indexOf(':');

        if (index >= 0) {
          String namespace = xml.getAttributeNamespace(key);

          if (namespace != null) {
            String prefix = key.substring(0, index);

            if (!nsprefixes.contains(prefix)) {
              this.writer.print(" xmlns:" + prefix);
              this.writer.print("=\"" + namespace + '"');
              nsprefixes.addElement(prefix);
            }
          }
        }
      }

      enumeration = xml.enumerateAttributeNames();

      while (enumeration.hasMoreElements()) {
        String key = (String)enumeration.nextElement();
        String value = xml.getAttribute(key, null);
        this.writer.print(" " + key + "=\"");
        this.writeEncoded(value);
        this.writer.print('"');
      }

      if ((xml.getContent() != null)
          && (xml.getContent().length() > 0)) {
        writer.print('>');
        this.writeEncoded(xml.getContent());
        writer.print("</" + xml.getFullName() + '>');

        if (prettyPrint) {
          writer.println();
        }
      }
      else if (xml.hasChildren() || (!collapseEmptyElements)) {
        writer.print('>');

        if (prettyPrint) {
          writer.println();
        }

        enumeration = xml.enumerateChildren();

        while (enumeration.hasMoreElements()) {
          IXMLElement child = (IXMLElement)enumeration.nextElement();
          this.write(child, prettyPrint, indent + 4,
                     collapseEmptyElements);
        }

        if (prettyPrint) {
          for (int i = 0; i < indent; i++) {
            this.writer.print(' ');
          }
        }

        this.writer.print("</" + xml.getFullName() + ">");

        if (prettyPrint) {
          writer.println();
        }
      }
      else {
        this.writer.print("/>");

        if (prettyPrint) {
          writer.println();
        }
      }
    }

    this.writer.flush();
  }


  /**
   * Writes a string encoding reserved characters.
   *
   * @param str the string to write.
   */
  private void writeEncoded(String str) {
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      switch (c) {
        case 0x0A:
          this.writer.print(c);
          break;

        case '<':
          this.writer.print("&lt;");
          break;

        case '>':
          this.writer.print("&gt;");
          break;

        case '&':
          this.writer.print("&amp;");
          break;

        case '\'':
          this.writer.print("&apos;");
          break;

        case '"':
          this.writer.print("&quot;");
          break;

        default:
          if ((c < ' ') || (c > 0x7E)) {
            this.writer.print("&#x");
            this.writer.print(Integer.toString(c, 16));
            this.writer.print(';');
          }
          else {
            this.writer.print(c);
          }
      }
    }
  }
}
