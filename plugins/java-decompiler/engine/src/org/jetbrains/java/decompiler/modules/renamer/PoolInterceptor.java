package org.jetbrains.java.decompiler.modules.renamer;

import java.util.HashMap;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;

public class PoolInterceptor {

	private IIdentifierRenamer helper;
	
	private HashMap<String, String> mapOldToNewNames = new HashMap<String, String>();
	
	private HashMap<String, String> mapNewToOldNames = new HashMap<String, String>();

	public PoolInterceptor(IIdentifierRenamer helper) {
		this.helper = helper;
	}
	
	public void addName(String oldName, String newName) {
		mapOldToNewNames.put(oldName, newName);
		mapNewToOldNames.put(newName, oldName);
	}

	public String getName(String oldName) {
		return mapOldToNewNames.get(oldName);
	}

	public String getOldName(String newName) {
		return mapNewToOldNames.get(newName);
	}

	public IIdentifierRenamer getHelper() {
		return helper;
	}
	
}
