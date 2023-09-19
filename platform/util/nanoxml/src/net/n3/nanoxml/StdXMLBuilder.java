/* StdXMLBuilder.java                                              NanoXML/Java
 *
 * $Revision: 1.3 $
 * $Date: 2002/01/04 21:03:28 $
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
import java.io.Reader;
import java.util.Stack;


/**
 * StdXMLBuilder is a concrete implementation of IXMLBuilder which creates a
 * tree of IXMLElement from an XML data source.
 *
 * @see net.n3.nanoxml.XMLElement
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 */
public class StdXMLBuilder
   implements IXMLBuilder
{

   /**
    * This stack contains the current element and its parents.
    */
   private Stack stack;


   /**
    * The root element of the parsed XML tree.
    */
   private IXMLElement root;


   /**
    * Prototype element for creating the tree.
    */
   private IXMLElement prototype;


   /**
    * Creates the builder.
    */
   public StdXMLBuilder()
   {
      this(new XMLElement());
   }


   /**
    * Creates the builder.
    *
    * @param prototype the prototype to use when building the tree.
    */
   public StdXMLBuilder(IXMLElement prototype)
   {
      this.stack = null;
      this.root = null;
      this.prototype = prototype;
   }


   /**
    * Cleans up the object when it's destroyed.
    */
   protected void finalize()
      throws Throwable
   {
      this.prototype = null;
      this.root = null;
      this.stack.clear();
      this.stack = null;
      super.finalize();
   }


   /**
    * This method is called before the parser starts processing its input.
    *
    * @param systemID the system ID of the XML data source.
    * @param lineNr   the line on which the parsing starts.
    */
   public void startBuilding(String systemID,
                             int    lineNr)
   {
      this.stack = new Stack();
      this.root = null;
   }


   /**
    * This method is called when a processing instruction is encountered.
    * PIs with target "xml" are handled by the parser.
    *
    * @param target the PI target.
    * @param reader to read the data from the PI.
    */
   public void newProcessingInstruction(String target,
                                        Reader reader)
   {
      // nothing to do
   }


   /**
    * This method is called when a new XML element is encountered.
    *
    * @see #endElement
    *
    * @param name       the name of the element.
    * @param nsPrefix   the prefix used to identify the namespace. If no
    *                   namespace has been specified, this parameter is null.
    * @param nsURI      the URI associated with the namespace. If no
    *                   namespace has been specified, or no URI is
    *                   associated with nsPrefix, this parameter is null.
    * @param systemID   the system ID of the XML data source.
    * @param lineNr     the line in the source where the element starts.
    */
   public void startElement(String name,
                            String nsPrefix,
                            String nsURI,
                            String systemID,
                            int    lineNr)
   {
      String fullName = name;

      if (nsPrefix != null) {
         fullName = nsPrefix + ':' + name;
      }

      IXMLElement elt = this.prototype.createElement(fullName, nsURI,
                                                     systemID, lineNr);

      if (this.stack.empty()) {
         this.root = elt;
      } else {
         IXMLElement top = (IXMLElement) this.stack.peek();
         top.addChild(elt);
      }

      this.stack.push(elt);
   }


   /**
    * This method is called when the attributes of an XML element have been
    * processed.
    *
    * @see #startElement
    * @see #addAttribute
    *
    * @param name       the name of the element.
    * @param nsPrefix   the prefix used to identify the namespace. If no
    *                   namespace has been specified, this parameter is null.
    * @param nsURI      the URI associated with the namespace. If no
    *                   namespace has been specified, or no URI is
    *                   associated with nsPrefix, this parameter is null.
    */
   public void elementAttributesProcessed(String name,
                                          String nsPrefix,
                                          String nsURI)
   {
      // nothing to do
   }


   /**
    * This method is called when the end of an XML elemnt is encountered.
    *
    * @see #startElement
    *
    * @param name       the name of the element.
    * @param nsPrefix   the prefix used to identify the namespace. If no
    *                   namespace has been specified, this parameter is null.
    * @param nsURI      the URI associated with the namespace. If no
    *                   namespace has been specified, or no URI is
    *                   associated with nsPrefix, this parameter is null.
    */
   public void endElement(String name,
                          String nsPrefix,
                          String nsURI)
   {
      IXMLElement elt = (IXMLElement) this.stack.pop();

      if (elt.getChildrenCount() == 1) {
         IXMLElement child = elt.getChildAtIndex(0);

         if (child.getName() == null) {
            elt.setContent(child.getContent());
            elt.removeChildAtIndex(0);
         }
      }
   }


   /**
    * This method is called when a new attribute of an XML element is
    * encountered.
    *
    * @param key        the key (name) of the attribute.
    * @param nsPrefix   the prefix used to identify the namespace. If no
    *                   namespace has been specified, this parameter is null.
    * @param nsURI      the URI associated with the namespace. If no
    *                   namespace has been specified, or no URI is
    *                   associated with nsPrefix, this parameter is null.
    * @param value      the value of the attribute.
    * @param type       the type of the attribute. If no type is known,
    *                   "CDATA" is returned.
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void addAttribute(String key,
                            String nsPrefix,
                            String nsURI,
                            String value,
                            String type)
      throws Exception
   {
      String fullName = key;

      if (nsPrefix != null) {
         fullName = nsPrefix + ':' + key;
      }

      IXMLElement top = (IXMLElement) this.stack.peek();

      if (top.hasAttribute(fullName)) {
         throw new XMLParseException(top.getSystemID(),
                                     top.getLineNr(),
                                     "Duplicate attribute: " + key);
      }

      if (nsPrefix != null) {
         top.setAttribute(fullName, nsURI, value);
      } else {
         top.setAttribute(fullName, value);
      }
   }


   /**
    * This method is called when a PCDATA element is encountered. A Java
    * reader is supplied from which you can read the data. The reader will
    * only read the data of the element. You don't need to check for
    * boundaries. If you don't read the full element, the rest of the data
    * is skipped. You also don't have to care about entities; they are
    * resolved by the parser.
    *
    * @param reader   the Java reader from which you can retrieve the data.
    * @param systemID the system ID of the XML data source.
    * @param lineNr   the line in the source where the element starts.
    */
   public void addPCData(Reader reader,
                         String systemID,
                         int    lineNr)
   {
      int bufSize = 2048;
      int sizeRead = 0;
      StringBuffer str = new StringBuffer(bufSize);
      char[] buf = new char[bufSize];

      for (;;) {
         if (sizeRead >= bufSize) {
            bufSize *= 2;
            str.ensureCapacity(bufSize);
         }

         int size;

         try {
            size = reader.read(buf);
         } catch (IOException e) {
            break;
         }

         if (size < 0) {
            break;
         }

         str.append(buf, 0, size);
         sizeRead += size;
      }

      IXMLElement elt = this.prototype.createElement(null, systemID, lineNr);
      elt.setContent(str.toString());

      if (! this.stack.empty()) {
         IXMLElement top = (IXMLElement) this.stack.peek();
         top.addChild(elt);
      }
   }


   /**
    * Returns the result of the building process. This method is called just
    * before the <I>parse</I> method of IXMLParser returns.
    *
    * @see net.n3.nanoxml.IXMLParser#parse
    *
    * @return the result of the building process.
    */
   public Object getResult()
   {
      return this.root;
   }

}
