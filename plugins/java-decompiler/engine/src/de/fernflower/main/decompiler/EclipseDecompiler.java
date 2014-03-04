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

package de.fernflower.main.decompiler;

import java.util.HashMap;

import de.fernflower.main.DecompilerContext;
import de.fernflower.main.Fernflower;
import de.fernflower.main.extern.IBytecodeProvider;
import de.fernflower.main.extern.IDecompilatSaver;
import de.fernflower.main.extern.IFernflowerLogger;


public class EclipseDecompiler {

	private Fernflower fernflower;
	
	public EclipseDecompiler(IBytecodeProvider provider,
			IDecompilatSaver saver, IFernflowerLogger logger,
			HashMap<String, Object> propertiesCustom) {

		fernflower = new Fernflower(provider, saver, propertiesCustom);
		
		DecompilerContext.setLogger(logger);
		
	}
	
	public void decompileContext() {
		fernflower.decompileContext();
	}
	
}
