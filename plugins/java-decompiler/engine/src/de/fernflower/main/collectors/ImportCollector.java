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

package org.jetbrains.java.decompiler.main.collectors;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.struct.StructContext;


public class ImportCollector {

	private static final String JAVA_LANG_PACKAGE = "java.lang";
	
	private HashMap<String, String> mapSimpleNames = new HashMap<String, String>();
	
	private HashSet<String> setNotImportedNames = new HashSet<String>();
	
	private String currentPackageSlash = ""; 
	
	private String currentPackagePoint = ""; 
	
	public ImportCollector(ClassNode root) {
		
		String clname = root.classStruct.qualifiedName;
		int index = clname.lastIndexOf("/");
		if(index >= 0) {
			currentPackageSlash = clname.substring(0, index);
			currentPackagePoint = currentPackageSlash.replace('/', '.');
			currentPackageSlash += "/";
		}
	}

	public String getShortName(String fullname) {
		return getShortName(fullname, true);
	}
	
	public String getShortName(String fullname, boolean imported) {
		
		ClassesProcessor clproc = DecompilerContext.getClassprocessor();
		ClassNode node = clproc.getMapRootClasses().get(fullname.replace('.', '/'));
		
		String retname = null;
		
		if(node != null && node.classStruct.isOwn()) {  
			
			retname = node.simpleName;
			
			while(node.parent != null && node.type == ClassNode.CLASS_MEMBER) {
				retname = node.parent.simpleName+"."+retname;
				node = node.parent;
			}
			
			if(node.type == ClassNode.CLASS_ROOT) {
				fullname = node.classStruct.qualifiedName;
				fullname = fullname.replace('/', '.');
			} else {
				return retname;
			}
			
		} else if(node == null || !node.classStruct.isOwn()) {
				fullname = fullname.replace('$', '.');
		}
		
		String nshort = fullname;
		String npackage = "";
		
		int lastpoint = fullname.lastIndexOf(".");
		
		if(lastpoint >= 0) {
			nshort = fullname.substring(lastpoint+1);
			npackage = fullname.substring(0, lastpoint);
		} 
		
		StructContext context = DecompilerContext.getStructcontext();
		
		boolean existsDefaultClass = (context.getClass(currentPackageSlash+nshort) != null 
													&& !npackage.equals(currentPackagePoint)) // current package
										|| (context.getClass(nshort) != null);  // default package
		
		if(existsDefaultClass || 
				(mapSimpleNames.containsKey(nshort) && !npackage.equals(mapSimpleNames.get(nshort)))) {
			return fullname;
		} else if(!mapSimpleNames.containsKey(nshort)) {
			mapSimpleNames.put(nshort, npackage);
			
			if(!imported) {
				setNotImportedNames.add(nshort);
			}
		}
		
		return retname==null?nshort:retname;
	}
	
	public void writeImports(BufferedWriter writer) throws IOException {

		for(String s: packImports()) {
            writer.write("import ");
            writer.write(s);
            writer.write(";");
            writer.write(DecompilerContext.getNewLineSeparator());
		}
		
	}
	
	private List<String> packImports() {
		
		List<Entry<String, String>> lst = new ArrayList<Entry<String, String>>(mapSimpleNames.entrySet());
		
		Collections.sort(lst, new Comparator<Entry<String, String>>() {
			public int compare(Entry<String, String> par0, Entry<String, String> par1) {
				int	res = par0.getValue().compareTo(par1.getValue());
				if(res == 0) {
					res = par0.getKey().compareTo(par1.getKey());
				}
				return res;
			}
		});
		
		List<String> res = new ArrayList<String>();
		for(Entry<String, String> ent: lst) {
			if(!setNotImportedNames.contains(ent.getKey()) // not the current class or one of the nested ones. Also not the empty package.
 					&& !JAVA_LANG_PACKAGE.equals(ent.getValue())
					&& ent.getValue().length() > 0) {  

				String imp = ent.getValue()+"."+ent.getKey();
				res.add(imp);
			}
		}
		
		return res;
	}
	
	
}
