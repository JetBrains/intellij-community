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

package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;

public class VarVersionPaar {

	public int var;
	public int version;
	
	private int hashCode = -1;
	
	public VarVersionPaar(int var, int version) {
		this.var = var;
		this.version = version;
	}

	public VarVersionPaar(Integer var, Integer version) {
		this.var = var.intValue();
		this.version = version.intValue();
	}

	public VarVersionPaar(VarExprent var) {
		this.var = var.getIndex();
		this.version = var.getVersion();
	}
	
	@Override
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof VarVersionPaar)) return false;
		
		VarVersionPaar paar = (VarVersionPaar)o;
		return var == paar.var && version == paar.version;
	}

	@Override
	public int hashCode() {
		if(hashCode == -1) {
			hashCode = this.var * 3 + this.version;
		}
		return hashCode;
	}

	@Override
	public String toString() {
		return "("+var+","+version+")";
	}
	
	
	
}
