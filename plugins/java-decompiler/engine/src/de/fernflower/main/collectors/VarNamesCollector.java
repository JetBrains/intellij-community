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

package de.fernflower.main.collectors;

import java.util.HashSet;

public class VarNamesCollector {

	private HashSet<String> usedNames = new HashSet<String>();
	
	public VarNamesCollector() {}

	public VarNamesCollector(HashSet<String> setNames) {
		usedNames.addAll(setNames);
	}
	
	public void addName(String value) {
		usedNames.add(value);
	}

	public String getFreeName(int index) {
		return getFreeName("var"+index); 
	}
	
	public String getFreeName(String proposition) {
		
		while(usedNames.contains(proposition)) {
			proposition+="x";
		}
		usedNames.add(proposition);
		return proposition;
	}

	public HashSet<String> getUsedNames() {
		return usedNames;
	}
	
}
