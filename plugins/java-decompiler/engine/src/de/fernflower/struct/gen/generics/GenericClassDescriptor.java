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

package org.jetbrains.java.decompiler.struct.gen.generics;

import java.util.ArrayList;
import java.util.List;

public class GenericClassDescriptor {

	public GenericType superclass;
	
	public List<GenericType> superinterfaces = new ArrayList<GenericType>();
	
	public List<String> fparameters = new ArrayList<String>();

	public List<List<GenericType>> fbounds = new ArrayList<List<GenericType>>();
	
}
