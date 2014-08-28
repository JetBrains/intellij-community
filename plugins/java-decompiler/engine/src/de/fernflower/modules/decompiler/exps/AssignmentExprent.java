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
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;


public class AssignmentExprent extends Exprent {

	public static final int CONDITION_NONE = -1;
	
	private static final String[] funceq = new String[] {
		  " += ", 	//  FUNCTION_ADD
		  " -= ",	//	FUNCTION_SUB
		  " *= ",	//	FUNCTION_MUL
		  " /= ",	//	FUNCTION_DIV
		  " &= ",	//	FUNCTION_AND
		  " |= ",	//	FUNCTION_OR
		  " ^= ",	//	FUNCTION_XOR
		  " %= ",	//	FUNCTION_REM
		  " <<= ",	//	FUNCTION_SHL
		  " >>= ",	//	FUNCTION_SHR
		  " >>>= "	//	FUNCTION_USHR
		};

	
	private Exprent left;
	
	private Exprent right;
	
	private int condtype = CONDITION_NONE;

	{
		this.type = EXPRENT_ASSIGNMENT;
	}
	
	
	public AssignmentExprent(Exprent left, Exprent right) {
		this.left = left;
		this.right = right;
	}
	
	
	public VarType getExprType() {
		return left.getExprType();
	}
	

	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();
		
		VarType typeleft = left.getExprType();
		VarType typeright = right.getExprType();
		
		if(typeleft.type_family > typeright.type_family) {
			result.addMinTypeExprent(right, VarType.getMinTypeInFamily(typeleft.type_family));
		} else if(typeleft.type_family < typeright.type_family) {
			result.addMinTypeExprent(left, typeright);
		} else {
			result.addMinTypeExprent(left, VarType.getCommonSupertype(typeleft, typeright));
		}
		
		return result;
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		lst.add(left); 
		lst.add(right);
		return lst;
	}
	
	public Exprent copy() {
		return new AssignmentExprent(left.copy(), right.copy());
	}
	
	public int getPrecedence() {
		return 13;
	}
	
	public String toJava(int indent) {
		
		VarType leftType = left.getExprType(); 
		VarType rightType = right.getExprType();
		
		String res = right.toJava(indent);
		
		if(condtype == CONDITION_NONE && !leftType.isSuperset(rightType) && (rightType.equals(VarType.VARTYPE_OBJECT) || leftType.type != CodeConstants.TYPE_OBJECT)) {
			if(right.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
				res = "("+res+")";
			}

			res = "("+ExprProcessor.getCastTypeName(leftType)+")"+res;
		}
		
		StringBuilder buffer = new StringBuilder();
		
		boolean finstat_init = false;
		if(left.type == Exprent.EXPRENT_FIELD) { // first assignment to a final field. Field name without "this" in front of it 
			FieldExprent field = (FieldExprent)left;
			if(field.isStatic()) {
				ClassNode node = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE));
				if(node != null) {
					StructClass cl = node.classStruct;
					StructField fd = cl.getField(field.getName(), field.getDescriptor().descriptorString);
					
					if(fd != null && (fd.access_flags & CodeConstants.ACC_FINAL) != 0) {
						finstat_init = true;
					}
				}
			}
		}
		
		if(finstat_init) {
			buffer.append(((FieldExprent)left).getName());
		} else {
			buffer.append(left.toJava(indent));
		}
		
		buffer.append(condtype == CONDITION_NONE ? " = " : funceq[condtype]).append(res);
		
		return buffer.toString();
	}
	
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof AssignmentExprent)) return false;

    AssignmentExprent as = (AssignmentExprent)o;
    return InterpreterUtil.equalObjects(left, as.getLeft()) &&
        InterpreterUtil.equalObjects(right, as.getRight()) &&
        condtype == as.getCondtype();
  }
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == left) {
			left = newexpr;
		} 
		
		if(oldexpr == right) {
			right = newexpr;
		}
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public Exprent getLeft() {
		return left;
	}

	public void setLeft(Exprent left) {
		this.left = left;
	}

	public Exprent getRight() {
		return right;
	}

	public void setRight(Exprent right) {
		this.right = right;
	}

	public int getCondtype() {
		return condtype;
	}

	public void setCondtype(int condtype) {
		this.condtype = condtype;
	}
}
