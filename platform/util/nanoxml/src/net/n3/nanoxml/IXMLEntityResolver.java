/* IXMLEntityResolver.java                                         NanoXML/Java
 *
 * $Revision: 1.4 $
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
 * An IXMLEntityResolver resolves entities.
 *
 * @author Marc De Scheemaecker
 * @version $Name: RELEASE_2_2_1 $, $Revision: 1.4 $
 */
public interface IXMLEntityResolver
{

   /**
    * Adds an internal entity.
    *
    * @param name  the name of the entity.
    * @param value the value of the entity.
    */
   public void addInternalEntity(String name,
                                 String value);


   /**
    * Adds an external entity.
    *
    * @param name     the name of the entity.
    * @param publicID the public ID of the entity, which may be null.
    * @param systemID the system ID of the entity.
    */
   public void addExternalEntity(String name,
                                 String publicID,
                                 String systemID);


   /**
    * Returns a Java reader containing the value of an entity.
    *
    * @param xmlReader the current NanoXML reader.
    * @param name      the name of the entity.
    *
    * @return the reader, or null if the entity could not be resolved.
    *
    * @throws net.n3.nanoxml.XMLParseException
    *     If an exception occurred while resolving the entity.
    */
   public Reader getEntity(IXMLReader xmlReader,
                           String     name)
      throws XMLParseException;


   /**
    * Returns true if an entity is external.
    *
    * @param name the name of the entity.
    */
   public boolean isExternalEntity(String name);

}
