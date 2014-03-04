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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;

import de.fernflower.main.extern.IDecompilatSaver;
import de.fernflower.struct.lazy.LazyLoader;
import de.fernflower.struct.lazy.LazyLoader.Link;

public class ContextUnit {
	
	public static final int TYPE_FOLDER = 0;
	public static final int TYPE_JAR = 1;
	public static final int TYPE_ZIP = 2;
	
	private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	private int type;
	
	// relative path to jar/zip
	private String archivepath;
	
	// folder: relative path
	// archive: file name
	private String filename;
	
	private List<StructClass> classes = new ArrayList<StructClass>(); 

	// class file or jar/zip entry. Should, but doesn't have to be the same as qualifiedName of the class 
	private List<String> classentries = new ArrayList<String>(); 

	private List<String> direntries = new ArrayList<String>(); 
	
	private List<String[]> otherentries = new ArrayList<String[]>();  
	
	private Manifest manifest;
	
	private IDecompilatSaver decompilatSaver;
	
	private IDecompiledData decompiledData;

	private boolean own = true;
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************

	public ContextUnit(int type, String archivepath, String filename, boolean own, 
			IDecompilatSaver decompilatSaver, IDecompiledData decompiledData) {
		this.type = type;
		this.own = own;
		this.archivepath = archivepath;
		this.filename = filename;
		this.decompilatSaver = decompilatSaver;
		this.decompiledData = decompiledData;
	}
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public void addClass(StructClass cl, String entryname) {
		classes.add(cl);
		classentries.add(entryname);
	}
	
	public void addDirEntry(String entry) {
		direntries.add(entry); 
	}
	
	public void addOtherEntry(String fullpath, String entry) {
		otherentries.add(new String[]{fullpath, entry});
	}

	public void reload(LazyLoader loader) throws IOException {
		
		List<StructClass> lstClasses = new ArrayList<StructClass>();
		for(StructClass cl : classes) {
			String oldname = cl.qualifiedName;
			StructClass newcl = new StructClass(loader.getClassStream(oldname), cl.isOwn(), loader);
			
			lstClasses.add(newcl);
			
			Link lnk = loader.getClassLink(oldname);
			loader.removeClassLink(oldname);
			loader.addClassLink(newcl.qualifiedName, lnk);
		}
		
		classes = lstClasses;
	}
	
	public void save() {
		
		switch(type) {
		case TYPE_FOLDER:
			
			// create folder 
			decompilatSaver.saveFolder(filename);

			// non-class files
			for(String[] arr: otherentries) {
				decompilatSaver.copyFile(arr[0], filename, arr[0]);
			}
			
			// classes
			for(int i=0;i<classes.size();i++) {

				StructClass cl = classes.get(i);
				String entryname = classentries.get(i);
				
				entryname = decompiledData.getClassEntryName(cl, entryname);
				if(entryname != null) {
					String content = decompiledData.getClassContent(cl);
					if(content != null) {
						decompilatSaver.saveClassFile(filename, cl.qualifiedName, entryname, content);
					}
				}
			}
			
			break;
		case TYPE_JAR:
		case TYPE_ZIP:
			
			// create archive file
			decompilatSaver.saveFolder(archivepath);
			decompilatSaver.createArchive(archivepath, filename, manifest);
			
			// directory entries
			for(String direntry: direntries) {
				decompilatSaver.saveEntry(archivepath, filename, direntry, null);
			}

			// non-class entries
			for(String[] arr: otherentries) {
				// manifest was defined by constructor invocation 
				if(type != TYPE_JAR || !MANIFEST_ENTRY.equalsIgnoreCase(arr[1])) {
					decompilatSaver.copyEntry(arr[0], archivepath, filename, arr[1]);
				}
			}
			
			// classes
			for(int i=0;i<classes.size();i++) {

				StructClass cl = classes.get(i);
				String entryname = classentries.get(i);

				entryname = decompiledData.getClassEntryName(cl, entryname);
				if(entryname != null) {
					String content = decompiledData.getClassContent(cl);
					decompilatSaver.saveClassEntry(archivepath, filename, cl.qualifiedName, entryname, content);
				}
			}
			
			decompilatSaver.closeArchive(archivepath, filename);
		}
	}
	
	// *****************************************************************************
	// private methods
	// *****************************************************************************
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public void setManifest(Manifest manifest) {
		this.manifest = manifest;
	}

	public boolean isOwn() {
		return own;
	}

	public List<StructClass> getClasses() {
		return classes;
	}

	public int getType() {
		return type;
	}

	public void setDecompilatSaver(IDecompilatSaver decompilatSaver) {
		this.decompilatSaver = decompilatSaver;
	}

	public void setDecompiledData(IDecompiledData decompiledData) {
		this.decompiledData = decompiledData;
	}

}
