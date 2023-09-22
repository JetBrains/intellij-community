/* IXMLParser.java                                                 NanoXML/Java
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


/**
 * IXMLParser is the core parser of NanoXML.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 */
public interface IXMLParser {

  /**
   * Returns the reader from which the parser retrieves its data.
   *
   * @return the reader.
   */
  IXMLReader getReader();

  /**
   * Sets the reader from which the parser retrieves its data.
   *
   * @param reader the reader.
   */
  void setReader(IXMLReader reader);

  /**
   * Returns the builder which creates the logical structure of the XML data.
   *
   * @return the builder.
   */
  IXMLBuilder getBuilder();

  /**
   * Sets the builder which creates the logical structure of the XML data.
   *
   * @param builder the builder.
   */
  void setBuilder(IXMLBuilder builder);

  /**
   * Returns the validator that validates the XML data.
   *
   * @return the validator.
   */
  IXMLValidator getValidator();

  /**
   * Sets the validator that validates the XML data.
   *
   * @param validator the validator.
   */
  void setValidator(IXMLValidator validator);

  /**
   * Returns the entity resolver.
   *
   * @return the non-null resolver.
   */
  IXMLEntityResolver getResolver();

  /**
   * Sets the entity resolver.
   *
   * @param resolver the non-null resolver.
   */
  void setResolver(IXMLEntityResolver resolver);

  /**
   * Parses the data and lets the builder create the logical data structure.
   * The method returns the result of <I>getResult</I> of the builder. if an
   * error occurred while reading or parsing the data, the method may throw
   * an XMLException.
   *
   * @return the logical structure built by the builder.
   * @throws XMLException if an error occurred reading or parsing the data
   * @see IXMLBuilder#getResult
   */
  Object parse()
    throws XMLException;
}
