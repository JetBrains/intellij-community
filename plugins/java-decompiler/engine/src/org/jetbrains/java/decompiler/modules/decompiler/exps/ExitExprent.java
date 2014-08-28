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

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.attr.StructExceptionsAttribute;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;


public class ExitExprent extends Exprent {
	
	public static final int EXIT_RETURN = 0;
	public static final int EXIT_THROW = 1;
	
	// return or throw statement
	private int exittype;
	
	private Exprent value;
	
	private VarType rettype;

	{
		this.type = EXPRENT_EXIT;
	}
	
	public ExitExprent(int exittype, Exprent value, VarType rettype) {
		this.exittype = exittype;
		this.value = value;
		this.rettype = rettype;
	}
	
	public Exprent copy() {
		return new ExitExprent(exittype, value==null?null:value.copy(), rettype);
	}
	
	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();

		if(exittype == EXIT_RETURN && rettype.type!=CodeConstants.TYPE_VOID) {
			result.addMinTypeExprent(value, VarType.getMinTypeInFamily(rettype.type_family));
			result.addMaxTypeExprent(value, rettype);
		}
		
		return result;
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		if(value != null) {
			lst.add(value);
		}
		return lst;
	}
	
	public String toJava(int indent) {
		if(exittype == EXIT_RETURN) {
			StringBuilder buffer = new StringBuilder();
			
			if(rettype.type!=CodeConstants.TYPE_VOID) {
				buffer.append(" ");
				ExprProcessor.getCastedExprent(value, rettype, buffer, indent, false);
			}
			
			return "return"+buffer.toString();
		} else {
			
			MethodWrapper meth = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);			
			ClassNode node = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE));

			if(meth != null && node != null) {
				StructExceptionsAttribute attr = (StructExceptionsAttribute)meth.methodStruct.getAttributes().getWithKey("Exceptions");
				
				if(attr != null) {
					String classname = null;
					
					for(int i=0;i<attr.getThrowsExceptions().size();i++) {
						String excclassname = attr.getExcClassname(i, node.classStruct.getPool());
						if("java/lang/Throwable".equals(excclassname)) {
							classname = excclassname;
							break;
						} else if("java/lang/Exception".equals(excclassname)) {
							classname = excclassname;
						}
					}
				
					if(classname != null) {
						VarType exctype = new VarType(classname, true);

						StringBuilder buffer = new StringBuilder();
						ExprProcessor.getCastedExprent(value, exctype, buffer, indent, false);
						
						return "throw "+buffer.toString();
					}
				} 
			}
			
			return "throw "+value.toJava(indent);
		}
	}
	
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof ExitExprent)) return false;

    ExitExprent et = (ExitExprent)o;
    return exittype==et.getExittype() &&
        InterpreterUtil.equalObjects(value, et.getValue());
  }
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == value) {
			value = newexpr;
		} 
	}
	
	public int getExittype() {
		return exittype;
	}

	public Exprent getValue() {
		return value;
	}

	public VarType getRettype() {
		return rettype;
	}

}
