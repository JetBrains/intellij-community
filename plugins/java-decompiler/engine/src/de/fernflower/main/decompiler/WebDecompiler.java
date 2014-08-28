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

package org.jetbrains.java.decompiler.main.decompiler;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;


public class WebDecompiler extends ConsoleDecompiler {

	private HashMap<String, File> mapInputFilenames = new HashMap<String, File>();
	
	private HashSet<String> setClassFiles = new HashSet<String>();
	
	private File root;
	
	public WebDecompiler(IFernflowerLogger logger, HashMap<String, Object> propertiesCustom) {
		super(logger, propertiesCustom);
	}

	@Override
	public void decompileContext(File root) {
		this.root = root;
		super.decompileContext(root);
	}
	
	@Override
	public void copyFile(String source, String destpath, String destfilename) {
		super.copyFile(source, destpath, destfilename);
		mapInputFilenames.put(destfilename, new File(getAbsolutePath(destpath), destfilename));
	}

	@Override
	public void saveFile(String path, String filename, String content) {
		super.saveFile(path, filename, content);
		
		mapInputFilenames.put(setClassFiles.contains(filename)?
				filename.substring(0, filename.lastIndexOf(".java"))+".class":
				filename, new File(getAbsolutePath(path), filename));
	}
	
	@Override
	public void saveClassFile(String path, String qualifiedName, String entryName, String content) {
		setClassFiles.add(entryName);
		saveFile(path, entryName, content);
	}
	
	@Override
	public void closeArchive(String path, String archivename) {
		super.closeArchive(path, archivename);
		mapInputFilenames.put(archivename, new File(getAbsolutePath(path), archivename));
	}

	private String getAbsolutePath(String path) {
		return new File(root, path).getAbsolutePath();
	}

	public HashMap<String, File> getMapInputFilenames() {
		return mapInputFilenames;
	}
	
}

