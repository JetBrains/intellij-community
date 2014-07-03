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

package de.fernflower.main.rels;

import java.io.IOException;
import java.util.HashSet;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.collectors.VarNamesCollector;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.attr.StructGeneralAttribute;
import de.fernflower.struct.attr.StructLocalVariableTableAttribute;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.VBStyleCollection;

public class ClassWrapper {

	private StructClass classStruct;
	
	private HashSet<String> hideMembers = new HashSet<String>(); 
	
	private VBStyleCollection<Exprent, String> staticFieldInitializers = new VBStyleCollection<Exprent, String>();

	private VBStyleCollection<Exprent, String> dynamicFieldInitializers = new VBStyleCollection<Exprent, String>();
	
	private VBStyleCollection<MethodWrapper, String> methods = new VBStyleCollection<MethodWrapper, String>();
	
	
	public ClassWrapper(StructClass classStruct) {
		this.classStruct = classStruct;
	}
	
	@SuppressWarnings("deprecation")
	public void init() throws IOException {
		
		DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASS, classStruct);
		
		DecompilerContext.getLogger().startClass(classStruct.qualifiedName);

		// collect field names
		HashSet<String> setFieldNames = new HashSet<String>();
		for(StructField fd: classStruct.getFields()) {
			setFieldNames.add(fd.getName());
		}
		
		for(StructMethod mt: classStruct.getMethods()) {
			
			DecompilerContext.getLogger().startMethod(mt.getName()+" "+mt.getDescriptor());
			
			VarNamesCollector vc = new VarNamesCollector();
			DecompilerContext.setVarncollector(vc);
			
			CounterContainer counter = new CounterContainer();
			DecompilerContext.setCountercontainer(counter);
			
			DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD, mt);
			DecompilerContext.setProperty(DecompilerContext.CURRENT_METHOD_DESCRIPTOR, MethodDescriptor.parseDescriptor(mt.getDescriptor()));
			
			VarProcessor varproc = new VarProcessor();
			DecompilerContext.setProperty(DecompilerContext.CURRENT_VAR_PROCESSOR, varproc);
			
			Thread mtthread = null;
			RootStatement root = null;
			
			boolean isError = false;
			
			try {
				if(mt.containsCode()) {
					
					int maxsec = 10 * Integer.parseInt(DecompilerContext.getProperty(IFernflowerPreferences.MAX_PROCESSING_METHOD).toString());
					
					if(maxsec == 0) { // blocking wait
						root = MethodProcessorThread.codeToJava(mt, varproc);
					} else {
						MethodProcessorThread mtproc = new MethodProcessorThread(mt, varproc, DecompilerContext.getCurrentContext());
						mtthread = new Thread(mtproc);

						mtthread.start();

						int sec = 0; 
						while(mtthread.isAlive()) {

							synchronized(mtproc) {
								mtproc.wait(100);
							}

							if(maxsec > 0 && ++sec > maxsec) {
								DecompilerContext.getLogger().writeMessage("Processing time limit ("+maxsec+" sec.) for method " +
										mt.getName()+" "+mt.getDescriptor()+ " exceeded, execution interrupted.", IFernflowerLogger.ERROR);
								mtthread.stop();
								isError = true;
								break;
							}
						}

						if(!isError) {
							if(mtproc.getError() != null) {
								throw mtproc.getError();
							} else {
								root = mtproc.getRoot();
							}
						}
					}
					
				} else {
					boolean thisvar = (mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0;
					MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
					
					int paramcount = 0;
					if(thisvar) {
						varproc.getThisvars().put(new VarVersionPaar(0,0), classStruct.qualifiedName);
						paramcount = 1;
					}
					paramcount += md.params.length;
					
					int varindex = 0;
					for(int i=0;i<paramcount;i++) {
						varproc.setVarName(new VarVersionPaar(varindex, 0), vc.getFreeName(varindex));
	
						if(thisvar) {
							if(i==0) {
								varindex++;
							} else {
								varindex+=md.params[i-1].stack_size;
							}
						} else {
							varindex+=md.params[i].stack_size;
						}
					}
				}

			} catch(ThreadDeath ex) {
				try {
					if(mtthread != null) {
						mtthread.stop();
					}
				} catch(Throwable ignored) { }
				
				throw ex;
			} catch(Throwable ex) {
				DecompilerContext.getLogger().writeMessage("Method "+mt.getName()+" "+mt.getDescriptor()+" couldn't be decompiled.", ex);
				isError = true;
			}
			
			MethodWrapper meth = new MethodWrapper(root, varproc, mt, counter);
			meth.decompiledWithErrors = isError;
			
			methods.addWithKey(meth, InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));

			// rename vars so that no one has the same name as a field
			varproc.refreshVarNames(new VarNamesCollector(setFieldNames));

			// if debug information present and should be used
			if(DecompilerContext.getOption(IFernflowerPreferences.USE_DEBUG_VARNAMES)) {
				StructLocalVariableTableAttribute attr = (StructLocalVariableTableAttribute)mt.getAttributes().getWithKey(
						StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE);
				
				if(attr != null) {
					varproc.setDebugVarNames(attr.getMapVarNames());
				}
			}
			
			DecompilerContext.getLogger().endMethod();
		}

		DecompilerContext.getLogger().endClass();
	}
	
	public MethodWrapper getMethodWrapper(String name, String descriptor) {
		return methods.getWithKey(InterpreterUtil.makeUniqueKey(name, descriptor));
	}
	
	public StructClass getClassStruct() {
		return classStruct;
	}

	public VBStyleCollection<MethodWrapper, String> getMethods() {
		return methods;
	}

	public HashSet<String> getHideMembers() {
		return hideMembers;
	}

	public VBStyleCollection<Exprent, String> getStaticFieldInitializers() {
		return staticFieldInitializers;
	}

	public VBStyleCollection<Exprent, String> getDynamicFieldInitializers() {
		return dynamicFieldInitializers;
	}

}
