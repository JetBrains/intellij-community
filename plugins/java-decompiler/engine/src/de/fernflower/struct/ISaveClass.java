/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.struct;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public interface ISaveClass {

	public String getClassEntryName(StructClass cl, String entryname);
	
	public void saveClassToFile(StructClass cl, File file) throws FileNotFoundException, IOException;

	public void saveClassToStream(StructClass cl, DataOutputStream out);
	
}
