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
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.code.cfg.BasicBlock;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.SecondaryFunctionsHelper;
import de.fernflower.modules.decompiler.StatEdge;
import de.fernflower.modules.decompiler.exps.AssertExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.ExitExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FieldExprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.modules.decompiler.stats.BasicBlockStatement;
import de.fernflower.modules.decompiler.stats.IfStatement;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.stats.SequenceStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.struct.StructField;
import de.fernflower.struct.gen.FieldDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;

public class AssertProcessor {
	
	private static final VarType CLASS_ASSERTION_ERROR = new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/AssertionError");  

	public static void buildAssertions(ClassNode node) {

		ClassWrapper wrapper = node.wrapper;

		StructField field = findAssertionField(node);
		
		if(field != null) {

			String key = InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor());
			
			boolean res = false;
			
			for(MethodWrapper meth : wrapper.getMethods()) {
				RootStatement root = meth.root;
				if(root != null) {
					res |= replaceAssertions(root, wrapper.getClassStruct().qualifiedName, key);
				}
			}
			
			if(res) {
				// hide the helper field
				wrapper.getHideMembers().add(key);
			}
		}
		
	}
	
	private static StructField findAssertionField(ClassNode node) {
		
		ClassWrapper wrapper = node.wrapper;
		
		boolean nosynthflag = DecompilerContext.getOption(IFernflowerPreferences.SYNTHETIC_NOT_SET); 

		for(StructField fd: wrapper.getClassStruct().getFields()) {

			String keyField = InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor());
			
			// initializer exists
			if(wrapper.getStaticFieldInitializers().containsKey(keyField)) {

				int flags = fd.access_flags;
				boolean isSynthetic = (flags & CodeConstants.ACC_SYNTHETIC) != 0 || fd.getAttributes().containsKey("Synthetic");

				// access flags set
				if((flags & CodeConstants.ACC_STATIC) != 0 && (flags & CodeConstants.ACC_FINAL) != 0 &&
						(isSynthetic || nosynthflag)) {

					// field type boolean
					FieldDescriptor fdescr = FieldDescriptor.parseDescriptor(fd.getDescriptor());
					if(VarType.VARTYPE_BOOLEAN.equals(fdescr.type)) {

						Exprent initializer = wrapper.getStaticFieldInitializers().getWithKey(keyField);
						if(initializer.type == Exprent.EXPRENT_FUNCTION) {
							FunctionExprent fexpr = (FunctionExprent)initializer;
							
							if(fexpr.getFunctype() == FunctionExprent.FUNCTION_BOOLNOT && 
									fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_INVOCATION) {
								
								InvocationExprent invexpr = (InvocationExprent)fexpr.getLstOperands().get(0);
								
								if(invexpr.getInstance() != null && invexpr.getInstance().type == Exprent.EXPRENT_CONST && "desiredAssertionStatus".equals(invexpr.getName())
										&& "java/lang/Class".equals(invexpr.getClassname()) && invexpr.getLstParameters().isEmpty()) {
									
									ConstExprent cexpr = (ConstExprent)invexpr.getInstance();
									if(VarType.VARTYPE_CLASS.equals(cexpr.getConsttype())) {
										
										ClassNode nd = node;
										while(nd != null) {
											if(nd.wrapper.getClassStruct().qualifiedName.equals(cexpr.getValue())) {
												break;
											}
											nd = nd.parent;
										}
										
										if(nd != null) { // found enclosing class with the same name
											return fd;
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
	
	
	private static boolean replaceAssertions(Statement statement, String classname, String key) {

		boolean res = false;
		
		for(Statement st : statement.getStats()) {
			res |= replaceAssertions(st, classname, key);
		}
		
		boolean replaced = true;
		while(replaced) {
			replaced = false;
			
			for(Statement st : statement.getStats()) {
				if(st.type == Statement.TYPE_IF) {
					if(replaceAssertion(statement, (IfStatement)st, classname, key)) {
						replaced = true;
						break;
					}
				}
			}
			
			res |= replaced;
		}
		
		return res;
	}
	
	private static boolean replaceAssertion(Statement parent, IfStatement stat, String classname, String key) {
		
		Statement ifstat = stat.getIfstat();
		InvocationExprent throwError = isAssertionError(ifstat);
		
		if(throwError == null) {
			return false;
		}
		
		Object[] exprres = getAssertionExprent(stat.getHeadexprent().getCondition().copy(), classname, key);
		if(!(Boolean)exprres[1]) {
			return false;
		}
		
		List<Exprent> lstParams = new ArrayList<Exprent>();
		
		Exprent ascond = null, retcond = null;
		if(exprres[0] != null) {
			ascond = new FunctionExprent(FunctionExprent.FUNCTION_BOOLNOT, 
					Arrays.asList(new Exprent[]{(Exprent)exprres[0]}));
			retcond = SecondaryFunctionsHelper.propagateBoolNot(ascond);
		}
		
		lstParams.add(retcond==null?ascond:retcond);
		if(!throwError.getLstParameters().isEmpty()) {
			lstParams.add(throwError.getLstParameters().get(0));
		}
		
		AssertExprent asexpr = new AssertExprent(lstParams); 
		
		Statement newstat = new BasicBlockStatement(new BasicBlock(
				DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER)));
		newstat.setExprents(Arrays.asList(new Exprent[] {asexpr}));

		Statement first = stat.getFirst();
		
		if(stat.iftype == IfStatement.IFTYPE_IFELSE || (first.getExprents() != null &&
				!first.getExprents().isEmpty())) {

			first.removeSuccessor(stat.getIfEdge());
			first.removeSuccessor(stat.getElseEdge());
			
			List<Statement> lstStatements = new ArrayList<Statement>();
			if(first.getExprents() != null && !first.getExprents().isEmpty()) {
				lstStatements.add(first);
			}
			lstStatements.add(newstat);
			if(stat.iftype == IfStatement.IFTYPE_IFELSE) {
				lstStatements.add(stat.getElsestat());
			}
			
			SequenceStatement sequence = new SequenceStatement(lstStatements);
			sequence.setAllParent();

			for(int i=0;i<sequence.getStats().size()-1;i++) {
				sequence.getStats().get(i).addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, 
						sequence.getStats().get(i), sequence.getStats().get(i+1)));
			}
			
			if(stat.iftype == IfStatement.IFTYPE_IFELSE) {
				Statement ifelse = stat.getElsestat();
				
				List<StatEdge> lstSuccs = ifelse.getAllSuccessorEdges(); 
				if(!lstSuccs.isEmpty()) {
					StatEdge endedge = lstSuccs.get(0);
					if(endedge.closure == stat) {
						sequence.addLabeledEdge(endedge);
					}
				}
			}
			
			newstat = sequence;
		}
		
		newstat.getVarDefinitions().addAll(stat.getVarDefinitions());
		parent.replaceStatement(stat, newstat);
		
		return true;
	}
	
	private static InvocationExprent isAssertionError(Statement stat) {
		
		if(stat == null || stat.getExprents() == null || stat.getExprents().size() !=1) {
			return null;
		}

		Exprent expr = stat.getExprents().get(0);
		
		if(expr.type == Exprent.EXPRENT_EXIT) {
			ExitExprent exexpr = (ExitExprent)expr;
			if(exexpr.getExittype() == ExitExprent.EXIT_THROW && exexpr.getValue().type == Exprent.EXPRENT_NEW) {
				NewExprent nexpr = (NewExprent)exexpr.getValue();
				if(CLASS_ASSERTION_ERROR.equals(nexpr.getNewtype()) && nexpr.getConstructor() != null) {
					return nexpr.getConstructor();
				}
			}
		}		
		
		return null;
	}
	
	private static Object[] getAssertionExprent(Exprent exprent, String classname, String key) {
		
		if(exprent.type == Exprent.EXPRENT_FUNCTION) {
			FunctionExprent fexpr = (FunctionExprent)exprent;
			if(fexpr.getFunctype() == FunctionExprent.FUNCTION_CADD) {
				
				for(int i=0;i<2;i++) {
					Exprent param = fexpr.getLstOperands().get(i);
					
					if(isAssertionField(param, classname, key)) {
						return new Object[] {fexpr.getLstOperands().get(1-i), true};
					}
				}
				
				for(int i=0;i<2;i++) {
					Exprent param = fexpr.getLstOperands().get(i);
					
					Object[] res = getAssertionExprent(param, classname, key);
					if((Boolean)res[1]) {
						if(param != res[0]) {
							fexpr.getLstOperands().set(i, (Exprent)res[0]);
						}
						return new Object[] {fexpr, true};
					}
				}
			} else if(isAssertionField(fexpr, classname, key)) {
				// assert false;
				return new Object[] {null, true};
			}
		}
		
		return new Object[] {exprent, false};
	}
	
	private static boolean isAssertionField(Exprent exprent, String classname, String key) {
		
		if(exprent.type == Exprent.EXPRENT_FUNCTION) {
			FunctionExprent fparam = (FunctionExprent)exprent;
			if(fparam.getFunctype() == FunctionExprent.FUNCTION_BOOLNOT &&
					fparam.getLstOperands().get(0).type == Exprent.EXPRENT_FIELD) {
				FieldExprent fdparam = (FieldExprent)fparam.getLstOperands().get(0);
				if(classname.equals(fdparam.getClassname())
						&& key.equals(InterpreterUtil.makeUniqueKey(fdparam.getName(), fdparam.getDescriptor().descriptorString))) {
					return true;
				}
			}
		}

		return false;
	}
}
