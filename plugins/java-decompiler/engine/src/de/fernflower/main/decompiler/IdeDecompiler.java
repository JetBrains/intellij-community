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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import de.fernflower.main.DecompilerContext;
import de.fernflower.main.Fernflower;
import de.fernflower.main.extern.IBytecodeProvider;
import de.fernflower.main.extern.IDecompilatSaver;
import de.fernflower.main.extern.IFernflowerLogger;


public class IdeDecompiler {

	private Fernflower fernflower;
	
	public IdeDecompiler(IBytecodeProvider provider,
                       IDecompilatSaver saver, IFernflowerLogger logger,
                       HashMap<String, Object> propertiesCustom) {

		fernflower = new Fernflower(provider, saver, propertiesCustom);
		
		DecompilerContext.setLogger(logger);
		
	}

  public void addSpace(File file, boolean isOwn) throws IOException {
    fernflower.getStructcontext().addSpace(file, isOwn);
  }

  public void decompileContext() {
    try {
      fernflower.decompileContext();
    } finally {
      fernflower.clearContext();
    }
  }

}
