package org.jetbrains.java.decompiler.main.extern;


public interface IIdentifierRenamer {

	public static final int ELEMENT_CLASS = 1;
	
	public static final int ELEMENT_FIELD = 2;

	public static final int ELEMENT_METHOD = 3;
	
	
	public boolean toBeRenamed(int element_type, String classname, String element, String descriptor);
	
	public String getNextClassname(String fullname, String shortname);
	
	public String getNextFieldname(String classname, String field, String descriptor);

	public String getNextMethodname(String classname, String method, String descriptor);
}
