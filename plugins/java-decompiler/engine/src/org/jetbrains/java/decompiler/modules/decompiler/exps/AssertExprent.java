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

package org.jetbrains.java.decompiler.modules.decompiler.exps;

import java.util.List;

public class AssertExprent extends Exprent {
	
	private List<Exprent> parameters;
	
	{
		this.type = EXPRENT_ASSERT;
	}

	public AssertExprent(List<Exprent> parameters) {
		this.parameters = parameters;
	}
	
	public String toJava(int indent) {
		
		StringBuilder buffer = new StringBuilder();

		buffer.append("assert ");
		
		if(parameters.get(0) == null) {
			buffer.append("false");
		} else {
			buffer.append(parameters.get(0).toJava(indent));
		}
		if(parameters.size() > 1) {
			buffer.append(" : ");
			buffer.append(parameters.get(1).toJava(indent));
		}

		return buffer.toString();
	}
	

}
