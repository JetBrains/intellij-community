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
  public static StdXMLParser createDefaultXMLParser() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    IXMLBuilder builder = new StdXMLBuilder();
    StdXMLParser parser = new StdXMLParser();
    parser.setBuilder(builder);
    parser.setValidator(new NonValidator());
    return parser;
  }
}
