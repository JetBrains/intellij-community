/* XMLParserFactory.java                                           NanoXML/Java
 *
 * $Revision: 1.3 $
 * $Date: 2002/01/04 21:03:29 $
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
 * Creates an XML parser.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.3 $
 */
public final class XMLParserFactory {

  /**
   * The class name of the default XML parser.
   */
  public static final String DEFAULT_CLASS = "net.n3.nanoxml.StdXMLParser";


  /**
   * The Java properties key of the XML parser class name.
   */
  public static final String CLASS_KEY = "net.n3.nanoxml.XMLParser";


  /**
   * Creates a default parser.
   *
   * @return the non-null parser.
   * @throws ClassNotFoundException if the class of the parser or validator could not be found.
   * @throws InstantiationException if the parser could not be created
   * @throws IllegalAccessException if the parser could not be created
   * @see #DEFAULT_CLASS
   * @see #CLASS_KEY
   */
  public static IXMLParser createDefaultXMLParser() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    String className = System.getProperty(CLASS_KEY, DEFAULT_CLASS);
    return createXMLParser(className, new StdXMLBuilder());
  }


  /**
   * Creates a default parser.
   *
   * @param builder the XML builder.
   * @return the non-null parser.
   * @throws ClassNotFoundException if the class of the parser could not be found.
   * @throws InstantiationException if the parser could not be created
   * @throws IllegalAccessException if the parser could not be created
   * @see #DEFAULT_CLASS
   * @see #CLASS_KEY
   */
  public static IXMLParser createDefaultXMLParser(IXMLBuilder builder)
    throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    String className = System.getProperty(CLASS_KEY, DEFAULT_CLASS);
    return createXMLParser(className, builder);
  }


  /**
   * Creates a parser.
   *
   * @param className the name of the class of the XML parser
   * @param builder   the XML builder.
   * @return the non-null parser.
   * @throws ClassNotFoundException if the class of the parser could not be found.
   * @throws InstantiationException if the parser could not be created
   * @throws IllegalAccessException if the parser could not be created
   */
  public static IXMLParser createXMLParser(String className, IXMLBuilder builder)
    throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    Class cls = Class.forName(className);
    IXMLParser parser = (IXMLParser)cls.newInstance();
    parser.setBuilder(builder);
    parser.setValidator(new NonValidator());
    return parser;
  }
}
