/* IXMLValidator.java                                              NanoXML/Java
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


import java.util.Properties;


/**
 * IXMLValidator processes the DTD and handles entity references.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 */
public interface IXMLValidator
{

   /**
    * Sets the parameter entity resolver.
    *
    * @param resolver the entity resolver.
    */
   public void setParameterEntityResolver(IXMLEntityResolver resolver);


   /**
    * Returns the parameter entity resolver.
    *
    * @return the entity resolver.
    */
   public IXMLEntityResolver getParameterEntityResolver();


   /**
    * Parses the DTD. The validator object is responsible for reading the
    * full DTD.
    *
    * @param publicID       the public ID, which may be null.
    * @param reader         the reader to read the DTD from.
    * @param entityResolver the entity resolver.
    * @param external       true if the DTD is external.
    *
    * @throws java.lang.Exception
    *     If something went wrong.
    */
   public void parseDTD(String             publicID,
                        IXMLReader         reader,
                        IXMLEntityResolver entityResolver,
                        boolean            external)
      throws Exception;


   /**
    * Indicates that an element has been started.
    *
    * @param name       the name of the element.
    * @param systemId   the system ID of the XML data of the element.
    * @param lineNr     the line number in the XML data of the element.
    *
    * @throws java.lang.Exception
    *     If the element could not be validated.
    */
   public void elementStarted(String name,
                              String systemId,
                              int    lineNr)
      throws Exception;


   /**
    * Indicates that the current element has ended.
    *
    * @param name       the name of the element.
    * @param systemId   the system ID of the XML data of the element.
    * @param lineNr     the line number in the XML data of the element.
    *
    * @throws java.lang.Exception
    *     If the element could not be validated.
    */
   public void elementEnded(String name,
                            String systemId,
                            int    lineNr)
      throws Exception;


   /**
    * Indicates that an attribute has been added to the current element.
    *
    * @param key        the name of the attribute.
    * @param value      the value of the attribute.
    * @param systemId   the system ID of the XML data of the element.
    * @param lineNr     the line number in the XML data of the element.
    *
    * @throws java.lang.Exception
    *     If the attribute could not be validated.
    */
   public void attributeAdded(String key,
                              String value,
                              String systemId,
                              int    lineNr)
      throws Exception;


   /**
    * This method is called when the attributes of an XML element have been
    * processed.
    * If there are attributes with a default value which have not been
    * specified yet, they have to be put into <I>extraAttributes</I>.
    *
    * @param name            the name of the element.
    * @param extraAttributes where to put extra attributes.
    * @param systemId        the system ID of the XML data of the element.
    * @param lineNr          the line number in the XML data of the element.
    *
    * @throws java.lang.Exception
    *     if the element could not be validated.
    */
   public void elementAttributesProcessed(String     name,
                                          Properties extraAttributes,
                                          String     systemId,
                                          int        lineNr)
      throws Exception;


   /**
    * Indicates that a new #PCDATA element has been encountered.
    *
    * @param systemId the system ID of the XML data of the element.
    * @param lineNr   the line number in the XML data of the element.
    *
    * @throws java.lang.Exception
    *     if the element could not be validated.
    */
   public void PCDataAdded(String systemId,
                           int    lineNr)
      throws Exception;

}
