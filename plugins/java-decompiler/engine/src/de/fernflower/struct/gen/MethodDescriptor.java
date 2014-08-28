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

package org.jetbrains.java.decompiler.struct.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodDescriptor {

	public VarType[] params;
	
	public VarType ret;
	
	
	public static MethodDescriptor parseDescriptor(String mdescr) {
		
		MethodDescriptor md = new MethodDescriptor(); 
		
		List<String> lst = new ArrayList<String>();
		String[] pars = mdescr.split("[()]");
		
		String par = pars[1];
		
		int indexFrom = -1, ind,index = 0;
		int len = par.length(); 
		
		for(;index<len;index++) {
			
			switch(par.charAt(index)){
			case '[':
				if(indexFrom<0){
					indexFrom = index;
				}
				break;
			case 'L':
				ind = par.indexOf(";", index);
				lst.add(par.substring(indexFrom<0?index:indexFrom, ind+1));
				index = ind;
				indexFrom = -1;
				break;
			default:
				lst.add(par.substring(indexFrom<0?index:indexFrom, index+1));
				indexFrom = -1;
			}
		}
		
		lst.add(pars[2]);
		
		
		md.params = new VarType[lst.size()-1];
		
		int i = 0;
		for(;i<lst.size()-1;i++) {
			md.params[i] = new VarType(lst.get(i)); 
		}
		md.ret = new VarType(lst.get(i));
		
		return md;
	}
	
	public String getDescriptor() {
		String res = "(";
		
		for(int j = 0;j<params.length;j++) {
			res+=params[j].toString(); 
		}

		res+=")"+ret.toString();
		
		return res;
	}

	@Override
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof MethodDescriptor)) return false;

    MethodDescriptor md = (MethodDescriptor)o;
    return ret.equals(md.ret) && Arrays.equals(params, md.params);
  }

	@Override
	public int hashCode() {
    int result = ret.hashCode();
    result = 31 * result + params.length;
    return result;
	}

}
