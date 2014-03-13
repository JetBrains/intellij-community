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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.ExitExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FieldExprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.stats.BasicBlockStatement;
import de.fernflower.modules.decompiler.stats.CatchStatement;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.VBStyleCollection;

public class ClassReference14Processor {
	
	public ExitExprent bodyexprent; 

	public ExitExprent handlerexprent; 
	
	
	public ClassReference14Processor() {
		
		InvocationExprent invfor = new InvocationExprent();
		invfor.setName("forName");
		invfor.setClassname("java/lang/Class");
		invfor.setStringDescriptor("(Ljava/lang/String;)Ljava/lang/Class;");
		invfor.setDescriptor(MethodDescriptor.parseDescriptor("(Ljava/lang/String;)Ljava/lang/Class;"));
		invfor.setStatic(true);
		invfor.setLstParameters(Arrays.asList(new Exprent[] {new VarExprent(0, VarType.VARTYPE_STRING, null)}));
		
		bodyexprent = new ExitExprent(ExitExprent.EXIT_RETURN, 
				invfor, 
				VarType.VARTYPE_CLASS);

		InvocationExprent constr = new InvocationExprent();
		constr.setName("<init>");
		constr.setClassname("java/lang/NoClassDefFoundError");
		constr.setStringDescriptor("()V");
		constr.setFunctype(InvocationExprent.TYP_INIT);
		constr.setDescriptor(MethodDescriptor.parseDescriptor("()V"));
		
		NewExprent newexpr = new NewExprent(new VarType(CodeConstants.TYPE_OBJECT,0,"java/lang/NoClassDefFoundError"), new ArrayList<Exprent>());
		newexpr.setConstructor(constr);
		
		InvocationExprent invcause = new InvocationExprent();
		invcause.setName("initCause");
		invcause.setClassname("java/lang/NoClassDefFoundError");
		invcause.setStringDescriptor("(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
		invcause.setDescriptor(MethodDescriptor.parseDescriptor("(Ljava/lang/Throwable;)Ljava/lang/Throwable;"));
		invcause.setInstance(newexpr);
		invcause.setLstParameters(Arrays.asList(new Exprent[] {new VarExprent(2, new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/ClassNotFoundException"), null)}));
		
		handlerexprent = new ExitExprent(ExitExprent.EXIT_THROW, 
				invcause, 
				null);
	}
	
	
	public void processClassReferences(ClassNode node) {
		
		ClassWrapper wrapper = node.wrapper;
		
//		int major_version = wrapper.getClassStruct().major_version;
//		int minor_version = wrapper.getClassStruct().minor_version;
//		
//		if(major_version > 48 || (major_version == 48 && minor_version > 0)) {
//			// version 1.5 or above
//			return;
//		}

		if(wrapper.getClassStruct().isVersionGE_1_5()) {
			// version 1.5 or above
			return;
		}
		
		// find the synthetic method Class class$(String) if present
		HashMap<ClassWrapper, MethodWrapper> mapClassMeths = new HashMap<ClassWrapper, MethodWrapper>(); 
		findClassMethod(node, mapClassMeths);
		
		if(mapClassMeths.isEmpty()) {
			return;
		}

		HashSet<ClassWrapper> setFound = new HashSet<ClassWrapper>(); 
		processClassRec(node, mapClassMeths, setFound);
		
		if(!setFound.isEmpty()) {
			for(ClassWrapper wrp : setFound) {
				StructMethod mt = mapClassMeths.get(wrp).methodStruct;
				wrp.getHideMembers().add(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
			}
		}
		
	}
	
	private void processClassRec(ClassNode node, final HashMap<ClassWrapper, MethodWrapper> mapClassMeths, final HashSet<ClassWrapper> setFound) {
		
		final ClassWrapper wrapper = node.wrapper;
		
		// search code
		for(MethodWrapper meth : wrapper.getMethods()) {

			RootStatement root = meth.root;
			if(root != null) {

				DirectGraph graph = meth.getOrBuildGraph();

				graph.iterateExprents(new DirectGraph.ExprentIterator() {
					public int processExprent(Exprent exprent) {
						for(Entry<ClassWrapper, MethodWrapper> ent : mapClassMeths.entrySet()) {
							if(replaceInvocations(exprent, ent.getKey(), ent.getValue())) {
								setFound.add(ent.getKey());
							}
						}
						return 0;
					}
				});
				
			}
		}
		
		// search initializers
		for(int j=0;j<2;j++) {
			VBStyleCollection<Exprent, String> initializers = j==0?wrapper.getStaticFieldInitializers():wrapper.getDynamicFieldInitializers(); 
			
			for(int i=0; i<initializers.size();i++) {
				for(Entry<ClassWrapper, MethodWrapper> ent : mapClassMeths.entrySet()) {
					Exprent exprent = initializers.get(i);
					if(replaceInvocations(exprent, ent.getKey(), ent.getValue())) {
						setFound.add(ent.getKey());
					}
		
					String cl = isClass14Invocation(exprent, ent.getKey(), ent.getValue());
					if(cl != null) {
						initializers.set(i, new ConstExprent(VarType.VARTYPE_CLASS, cl.replace('.', '/')));
						setFound.add(ent.getKey());
					}
				}
			}
		}

		// iterate nested classes
		for(ClassNode nd : node.nested) {
			processClassRec(nd, mapClassMeths, setFound);
		}
		
	}
	
	private void findClassMethod(ClassNode node, HashMap<ClassWrapper, MethodWrapper> mapClassMeths) {
		
		boolean nosynthflag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET); 
		
		ClassWrapper wrapper = node.wrapper;
		
		for(MethodWrapper meth : wrapper.getMethods()) {
			StructMethod mt = meth.methodStruct;
			
			if(((mt.getAccessFlags() & CodeConstants.ACC_SYNTHETIC) != 0 || mt.getAttributes().containsKey("Synthetic")
					|| nosynthflag) && 
					mt.getDescriptor().equals("(Ljava/lang/String;)Ljava/lang/Class;") &&
					(mt.getAccessFlags() & CodeConstants.ACC_STATIC) != 0) {
				
				RootStatement root = meth.root;
				if(root != null) {
					if(root.getFirst().type == Statement.TYPE_TRYCATCH) {
						CatchStatement cst = (CatchStatement)root.getFirst();
						if(cst.getStats().size() == 2 && cst.getFirst().type == Statement.TYPE_BASICBLOCK &&
								cst.getStats().get(1).type == Statement.TYPE_BASICBLOCK &&
								cst.getVars().get(0).getVartype().equals(new VarType(CodeConstants.TYPE_OBJECT,0,"java/lang/ClassNotFoundException"))) {
							
							BasicBlockStatement body = (BasicBlockStatement)cst.getFirst();
							BasicBlockStatement handler = (BasicBlockStatement)cst.getStats().get(1);
							
							if(body.getExprents().size() == 1 && handler.getExprents().size() == 1) {
								if(bodyexprent.equals(body.getExprents().get(0)) && 
										handlerexprent.equals(handler.getExprents().get(0))) {
									mapClassMeths.put(wrapper, meth);
									break;
								}
							}
						}
					}
				}
			}
		}
		
		// iterate nested classes
		for(ClassNode nd : node.nested) {
			findClassMethod(nd, mapClassMeths);
		}
		
	}
	
	
	private boolean replaceInvocations(Exprent exprent, ClassWrapper wrapper, MethodWrapper meth) {
		
		boolean res = false;
		
		for(;;) {

			boolean found = false;
			
			for(Exprent expr : exprent.getAllExprents()) {
				String cl = isClass14Invocation(expr, wrapper, meth);
				if(cl != null) {
					exprent.replaceExprent(expr, new ConstExprent(VarType.VARTYPE_CLASS, cl.replace('.', '/')));
					found = true;
					res = true;
					break;
				}
				
				res |= replaceInvocations(expr, wrapper, meth);
			}
			
			if(!found) {
				break;
			}
		}
		
		return res;
	}
	
	
	
	private String isClass14Invocation(Exprent exprent, ClassWrapper wrapper, MethodWrapper meth) {
		
		if(exprent.type == Exprent.EXPRENT_FUNCTION) {
			FunctionExprent fexpr = (FunctionExprent)exprent;
			if(fexpr.getFunctype() == FunctionExprent.FUNCTION_IIF) {
				if(fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_FUNCTION) {
					FunctionExprent headexpr = (FunctionExprent)fexpr.getLstOperands().get(0);
					if(headexpr.getFunctype() == FunctionExprent.FUNCTION_EQ) {
						if(headexpr.getLstOperands().get(0).type == Exprent.EXPRENT_FIELD && 
								headexpr.getLstOperands().get(1).type == Exprent.EXPRENT_CONST &&
								((ConstExprent)headexpr.getLstOperands().get(1)).getConsttype().equals(VarType.VARTYPE_NULL)) {

							FieldExprent field = (FieldExprent)headexpr.getLstOperands().get(0);
							ClassNode fieldnode = DecompilerContext.getClassprocessor().getMapRootClasses().get(field.getClassname());
							
							if(fieldnode != null && fieldnode.classStruct.qualifiedName.equals(wrapper.getClassStruct().qualifiedName)) { // source class
								StructField fd = wrapper.getClassStruct().getField(field.getName(), field.getDescriptor().descriptorString);  // FIXME: can be null! why??
								
								if(fd != null && (fd.access_flags & CodeConstants.ACC_STATIC) != 0 && 
										((fd.access_flags & CodeConstants.ACC_SYNTHETIC) != 0 || fd.getAttributes().containsKey("Synthetic")
												|| DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET))) {
									
									if(fexpr.getLstOperands().get(1).type == Exprent.EXPRENT_ASSIGNMENT && fexpr.getLstOperands().get(2).equals(field)) {
										AssignmentExprent asexpr = (AssignmentExprent)fexpr.getLstOperands().get(1);
										
										if(asexpr.getLeft().equals(field) && asexpr.getRight().type == Exprent.EXPRENT_INVOCATION) {
											InvocationExprent invexpr = (InvocationExprent)asexpr.getRight();
											
											if(invexpr.getClassname().equals(wrapper.getClassStruct().qualifiedName) &&
													invexpr.getName().equals(meth.methodStruct.getName()) &&
													invexpr.getStringDescriptor().equals(meth.methodStruct.getDescriptor())) {
												
												if(invexpr.getLstParameters().get(0).type == Exprent.EXPRENT_CONST) {
													wrapper.getHideMembers().add(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));  // hide synthetic field
													return ((ConstExprent)invexpr.getLstParameters().get(0)).getValue().toString();
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		return null;
	}
}
