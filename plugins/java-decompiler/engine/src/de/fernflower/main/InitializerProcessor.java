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

package de.fernflower.main;

import java.util.ArrayList;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FieldExprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructField;
import de.fernflower.util.InterpreterUtil;


public class InitializerProcessor {

	public static void extractInitializers(ClassWrapper wrapper) {
		
		MethodWrapper meth = wrapper.getMethodWrapper("<clinit>", "()V");
		if(meth != null && meth.root != null) {  // successfully decompiled static constructor
			extractStaticInitializers(wrapper, meth);
		}
		
		extractDynamicInitializers(wrapper);
		
		// required e.g. if anonymous class is being decompiled as a standard one.
		// This can happen if InnerClasses attributes are erased
		liftConstructor(wrapper);
		
		if(DecompilerContext.getOption(IFernflowerPreferences.HIDE_EMPTY_SUPER)) {
			hideEmptySuper(wrapper);
		}
	}
	
	
	private static void liftConstructor(ClassWrapper wrapper) {  
		
		for(MethodWrapper meth : wrapper.getMethods()) {
			if("<init>".equals(meth.methodStruct.getName()) && meth.root != null) {
				Statement firstdata = findFirstData(meth.root);
				if(firstdata == null) {
					return;
				}

				
				int index = 0;
				List<Exprent> lstExprents = firstdata.getExprents();
				
				for(Exprent exprent : lstExprents) {

					int action = 0;
					
					if(exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
						AssignmentExprent asexpr = (AssignmentExprent)exprent;
						if(asexpr.getLeft().type == Exprent.EXPRENT_FIELD && asexpr.getRight().type == Exprent.EXPRENT_VAR) {
							FieldExprent fexpr = (FieldExprent)asexpr.getLeft();
							if(fexpr.getClassname().equals(wrapper.getClassStruct().qualifiedName)) {
								StructField structField = wrapper.getClassStruct().getField(fexpr.getName(), fexpr.getDescriptor().descriptorString);
								if(structField != null && (structField.access_flags & CodeConstants.ACC_FINAL) != 0) {
									action = 1;
								}
							}
						}
					} else if(index > 0 && exprent.type == Exprent.EXPRENT_INVOCATION && 
							isInvocationInitConstructor((InvocationExprent)exprent, meth, wrapper, true)) {
						// this() or super()
						lstExprents.add(0, lstExprents.remove(index));
						action = 2; 
					}
					
					if(action != 1) {
						break;
					}
					
					index++;
				}
			}
		}
	}
	
	
	private static void hideEmptySuper(ClassWrapper wrapper) {
		
		for(MethodWrapper meth : wrapper.getMethods()) {
			if("<init>".equals(meth.methodStruct.getName()) && meth.root != null) {
				Statement firstdata = findFirstData(meth.root);
				if(firstdata == null || firstdata.getExprents().isEmpty()) {
					return;
				}
				
				Exprent exprent = firstdata.getExprents().get(0);
				if(exprent.type == Exprent.EXPRENT_INVOCATION) {
					InvocationExprent invexpr = (InvocationExprent)exprent;
					if(isInvocationInitConstructor(invexpr, meth, wrapper, false) && invexpr.getLstParameters().isEmpty()) {
						firstdata.getExprents().remove(0);
					}
				}
			}
		}
	}
	
	private static void extractStaticInitializers(ClassWrapper wrapper, MethodWrapper meth) {
		
		RootStatement root = meth.root;
		StructClass cl = wrapper.getClassStruct();
		
		Statement firstdata = findFirstData(root);
		if(firstdata != null) {
			while(!firstdata.getExprents().isEmpty()) {
				Exprent exprent = firstdata.getExprents().get(0);
				
				boolean found = false;
				
				if(exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
					AssignmentExprent asexpr = (AssignmentExprent)exprent;
					if(asexpr.getLeft().type == Exprent.EXPRENT_FIELD) {
						FieldExprent fexpr = (FieldExprent)asexpr.getLeft();
						if(fexpr.isStatic() && fexpr.getClassname().equals(cl.qualifiedName)) {
							String keyField = InterpreterUtil.makeUniqueKey(fexpr.getName(), fexpr.getDescriptor().descriptorString);
							if(!wrapper.getStaticFieldInitializers().containsKey(keyField)) {
								wrapper.getStaticFieldInitializers().addWithKey(asexpr.getRight(), keyField);
								firstdata.getExprents().remove(0);
								found = true;
							}
						}
					}
				}
				
				if(!found) {
					break;
				}
			}
		}
	}
	
	private static void extractDynamicInitializers(ClassWrapper wrapper) {
		
		StructClass cl = wrapper.getClassStruct();

		boolean isAnonymous = DecompilerContext.getClassprocessor().getMapRootClasses().get(cl.qualifiedName).type == ClassNode.CLASS_ANONYMOUS; 
		
		List<List<Exprent>> lstFirst = new ArrayList<List<Exprent>>();
		List<MethodWrapper> lstMethWrappers = new ArrayList<MethodWrapper>();
		
		for(MethodWrapper meth : wrapper.getMethods()) {
			if("<init>".equals(meth.methodStruct.getName()) && meth.root != null) { // successfully decompiled constructor
				Statement firstdata = findFirstData(meth.root);
				if(firstdata == null || firstdata.getExprents().isEmpty()) {
					return;
				}
				lstFirst.add(firstdata.getExprents());
				lstMethWrappers.add(meth);
				
				Exprent exprent = firstdata.getExprents().get(0);
				if(!isAnonymous) { // FIXME: doesn't make sense 
					if(exprent.type != Exprent.EXPRENT_INVOCATION || !isInvocationInitConstructor((InvocationExprent)exprent, meth, wrapper, false)) {
						return;
					}
				}
			}
		}
		
		if(lstFirst.isEmpty()) {
			return;
		}
		
		for(;;) {
			
			String fieldWithDescr = null;
			Exprent value = null;
			
			for(int i=0; i<lstFirst.size(); i++) {
				
				List<Exprent> lst = lstFirst.get(i);
				
				if(lst.size() < (isAnonymous?1:2)) {
					return;
				}
				
				Exprent exprent = lst.get(isAnonymous?0:1);
				
				boolean found = false;
				
				if(exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
					AssignmentExprent asexpr = (AssignmentExprent)exprent;
					if(asexpr.getLeft().type == Exprent.EXPRENT_FIELD) {
						FieldExprent fexpr = (FieldExprent)asexpr.getLeft();
						if(!fexpr.isStatic() && fexpr.getClassname().equals(cl.qualifiedName) &&
								cl.hasField(fexpr.getName(), fexpr.getDescriptor().descriptorString)) { // check for the physical existence of the field. Could be defined in a superclass.
							
							if(isExprentIndependent(asexpr.getRight(), lstMethWrappers.get(i))) {
								String fieldKey = InterpreterUtil.makeUniqueKey(fexpr.getName(), fexpr.getDescriptor().descriptorString);
								if(fieldWithDescr == null) {
									fieldWithDescr = fieldKey;
									value = asexpr.getRight();
								} else {
									if(!fieldWithDescr.equals(fieldKey) ||
											!value.equals(asexpr.getRight())) {
										return; 
									}
								}
								found = true;
							}
						}
					}
				}
				
				if(!found) {
					return; 
				}
			}
			
			if(!wrapper.getDynamicFieldInitializers().containsKey(fieldWithDescr)) {
				wrapper.getDynamicFieldInitializers().addWithKey(value, fieldWithDescr);
				
				for(List<Exprent> lst : lstFirst) {
					lst.remove(isAnonymous?0:1);
				}
			} else {
				return;
			}
		}
		
	}
	
	private static boolean isExprentIndependent(Exprent exprent, MethodWrapper meth) {
		
		List<Exprent> lst = exprent.getAllExprents(true);
		lst.add(exprent);
		
		for(Exprent expr : lst) {
			switch(expr.type) {
			case Exprent.EXPRENT_VAR:
				VarVersionPaar varpaar = new VarVersionPaar((VarExprent)expr);
				if(!meth.varproc.getExternvars().contains(varpaar)) {
					String varname = meth.varproc.getVarName(varpaar);
					
					if(!varname.equals("this") && !varname.endsWith(".this")) { // FIXME: remove direct comparison with strings
						return false;
					}
				}
				break;
			case Exprent.EXPRENT_FIELD:
				return false;
			}
		}

		return true;
	}
	
	
	private static Statement findFirstData(Statement stat) {

		if(stat.getExprents() != null) {
			return stat;
		} else {
			if(stat.isLabeled()) { // FIXME: Why??
				return null;
			}
			
			switch(stat.type) {
			case Statement.TYPE_SEQUENCE:
			case Statement.TYPE_IF:
			case Statement.TYPE_ROOT:
			case Statement.TYPE_SWITCH:
			case Statement.TYPE_SYNCRONIZED:
				return findFirstData(stat.getFirst());
			default:
				return null;
			}
		}
	}
	
	private static boolean isInvocationInitConstructor(InvocationExprent inv, MethodWrapper meth, ClassWrapper wrapper, boolean withThis) {
		
		if(inv.getFunctype() == InvocationExprent.TYP_INIT) {
			if(inv.getInstance().type == Exprent.EXPRENT_VAR) {
				VarExprent instvar = (VarExprent)inv.getInstance();
				VarVersionPaar varpaar = new VarVersionPaar(instvar);
				
				String classname = meth.varproc.getThisvars().get(varpaar);
				
				if(classname!=null) { // any this instance. TODO: Restrict to current class? 
					if(withThis || !wrapper.getClassStruct().qualifiedName.equals(inv.getClassname())) {
						return true;
					}
				}
			}
		}
		
		return false;
	}
}
