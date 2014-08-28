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

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.util.InterpreterUtil;


public class MonitorExprent extends Exprent {

	public static final int MONITOR_ENTER = 0;
	public static final int MONITOR_EXIT = 1;
	
	private int montype;
	
	private Exprent value;
	
	{
		this.type = EXPRENT_MONITOR;
	}
	
	public MonitorExprent(int montype, Exprent value) {
		this.montype = montype;
		this.value = value;
	}
	
	public Exprent copy() {
		return new MonitorExprent(montype, value.copy());
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		lst.add(value);
		return lst;
	}
	
	public String toJava(int indent) {
		if(montype == MONITOR_ENTER) {
			return "synchronized("+value.toJava(indent)+")";
		} else {
			return "";
		}
	}
	
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof MonitorExprent)) return false;

    MonitorExprent me = (MonitorExprent)o;
    return montype == me.getMontype() &&
        InterpreterUtil.equalObjects(value, me.getValue());
  }

	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == value) {
			value = newexpr;
		} 
	}
	
	public int getMontype() {
		return montype;
	}

	public Exprent getValue() {
		return value;
	}
	
}
