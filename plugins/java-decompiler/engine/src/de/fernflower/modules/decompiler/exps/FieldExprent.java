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

package de.fernflower.modules.decompiler.exps;

import java.util.ArrayList;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.consts.LinkConstant;
import de.fernflower.struct.gen.FieldDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;


public class FieldExprent extends Exprent {

	private String name;
	
	private String classname;
	
	private boolean isStatic;
	
	private Exprent instance;
	
	private FieldDescriptor descriptor;
	
	{
		this.type = EXPRENT_FIELD;
	}

	public FieldExprent(LinkConstant cn, Exprent instance) {
		
		this.instance = instance;
		
		if(instance == null) {
			isStatic = true;
		}

		classname = cn.classname;
		name = cn.elementname;
		descriptor = FieldDescriptor.parseDescriptor(cn.descriptor);
	}
	
	public FieldExprent(String name, String classname, boolean isStatic, Exprent instance, FieldDescriptor descriptor) {
		this.name = name;
		this.classname = classname;
		this.isStatic = isStatic;
		this.instance = instance;
		this.descriptor = descriptor;
	}
	
	public VarType getExprType() {
		return descriptor.type;
	}
	
	public int getExprentUse() {
		if(instance == null) {
			return Exprent.MULTIPLE_USES;
		} else {
			return instance.getExprentUse() & Exprent.MULTIPLE_USES;
		}
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		if(instance != null) {
			lst.add(instance);
		}
		return lst;
	}
	
	public Exprent copy() {
		return new FieldExprent(name, classname, isStatic, instance==null?null:instance.copy(), descriptor);
	}
	
	public String toJava(int indent) {
		StringBuffer buf = new StringBuffer();
		
		
		if(isStatic) {
			ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE);
			if(node == null || !classname.equals(node.classStruct.qualifiedName)) {
				buf.append(DecompilerContext.getImpcollector().getShortName(ExprProcessor.buildJavaClassName(classname)));
				buf.append(".");
			}
		} else {
			
			String super_qualifier = null;

			if(instance != null && instance.type == Exprent.EXPRENT_VAR) {
				VarExprent instvar = (VarExprent)instance;
				VarVersionPaar varpaar = new VarVersionPaar(instvar);

				MethodWrapper current_meth = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
				
				if(current_meth != null) { // FIXME: remove
					String this_classname = current_meth.varproc.getThisvars().get(varpaar);
					
					if(this_classname != null) {
						if(!classname.equals(this_classname)) { // TODO: direct comparison to the super class?
							super_qualifier = this_classname;
						}
					}
				}
			}
			
			if(super_qualifier != null) {
				StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE)).classStruct;
				
				if(!super_qualifier.equals(current_class.qualifiedName)) {
					buf.append(DecompilerContext.getImpcollector().getShortName(ExprProcessor.buildJavaClassName(super_qualifier)));
					buf.append(".");
				}
				buf.append("super");
			} else {
				StringBuilder buff = new StringBuilder();
				boolean casted = ExprProcessor.getCastedExprent(instance, new VarType(CodeConstants.TYPE_OBJECT, 0, classname), buff, indent, true);
				String res = buff.toString();
				
				if(casted || instance.getPrecedence() > getPrecedence()) { 
					res = "("+res+")";
				}
				
				buf.append(res);
			}
			
			if(buf.toString().equals(VarExprent.VAR_NAMELESS_ENCLOSURE)) { // FIXME: workaround for field access of an anonymous enclosing class. Find a better way. 
				buf.setLength(0);
			} else {
				buf.append(".");
			}
		}
		
		buf.append(name);
		
		return buf.toString();
	}
	
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof FieldExprent)) return false;

    FieldExprent ft = (FieldExprent)o;
    return InterpreterUtil.equalObjects(name, ft.getName()) &&
        InterpreterUtil.equalObjects(classname, ft.getClassname()) &&
        isStatic == ft.isStatic() &&
        InterpreterUtil.equalObjects(instance, ft.getInstance()) &&
        InterpreterUtil.equalObjects(descriptor, ft.getDescriptor());
  }

	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == instance) {
			instance = newexpr;
		} 
	}
	
	public String getClassname() {
		return classname;
	}

	public FieldDescriptor getDescriptor() {
		return descriptor;
	}

	public Exprent getInstance() {
		return instance;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public String getName() {
		return name;
	}
	
}
