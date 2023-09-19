/* IXMLBuilder.java                                                NanoXML/Java
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


import java.io.Reader;


/**
 * NanoXML uses IXMLBuilder to construct the XML data structure it retrieved
 * from its data source. You can supply your own builder or you can use the
 * default builder of NanoXML.
 * <P>
 * If a method of the builder throws an exception, the parsing is aborted and
 * {@link net.n3.nanoxml.IXMLParser#parse} throws an
 * {@link net.n3.nanoxml.XMLException} which encasulates the original
 * exception.
 *
 * @see net.n3.nanoxml.IXMLParser
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 */
public interface IXMLBuilder
{

   /**
    * This method is called before the parser starts processing its input.
    *
    * @param systemID the system ID of the XML data source.
    * @param lineNr   the line on which the parsing starts.
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void startBuilding(String systemID,
                             int    lineNr)
      throws Exception;


   /**
    * This method is called when a processing instruction is encountered.
    * A PI with a reserved target ("xml" with any case) is never reported.
    *
    * @param target the processing instruction target.
    * @param reader the method can retrieve the parameter of the PI from this
    *               reader. You may close the reader before reading all its
    *               data and you cannot read too much data.
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void newProcessingInstruction(String target,
                                        Reader reader)
      throws Exception;


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
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void startElement(String name,
                            String nsPrefix,
                            String nsURI,
                            String systemID,
                            int    lineNr)
      throws Exception;


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
      throws Exception;


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
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void elementAttributesProcessed(String name,
                                          String nsPrefix,
                                          String nsURI)
      throws Exception;


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
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void endElement(String name,
                          String nsPrefix,
                          String nsURI)
      throws Exception;


   /**
    * This method is called when a PCDATA element is encountered. A Java
    * reader is supplied from which you can read the data. The reader will
    * only read the data of the element. You don't need to check for
    * boundaries. If you don't read the full element, the rest of the data
    * is skipped. You also don't have to care about entities: they are
    * resolved by the parser.
    *
    * @param reader   the method can retrieve the data from this reader. You
    *                 may close the reader before reading all its data and you
    *                 cannot read too much data.
    * @param systemID the system ID of the XML data source.
    * @param lineNr   the line in the source where the element starts.
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public void addPCData(Reader reader,
                         String systemID,
                         int    lineNr)
      throws Exception;


   /**
    * Returns the result of the building process. This method is called just
    * before the <I>parse</I> method of IXMLParser returns.
    *
    * @see net.n3.nanoxml.IXMLParser#parse
    *
    * @return the result of the building process.
    *
    * @throws java.lang.Exception
    *     If an exception occurred while processing the event.
    */
   public Object getResult()
      throws Exception;

}
