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

package org.jetbrains.java.decompiler.main.rels;

import java.util.HashSet;
import java.util.List;

import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.struct.StructMethod;


public class MethodWrapper {

	public RootStatement root;
	
	public VarProcessor varproc;
	
	public StructMethod methodStruct;
	
	public CounterContainer counter;
	
	public DirectGraph graph;
	
	public List<VarVersionPaar> signatureFields;
	
	public boolean decompiledWithErrors;
	
	public HashSet<String> setOuterVarNames = new HashSet<String>();  
	
	public MethodWrapper(RootStatement root, VarProcessor varproc, StructMethod methodStruct, CounterContainer counter) {
		this.root = root;
		this.varproc = varproc;
		this.methodStruct = methodStruct;
		this.counter = counter;
	}
	
	public DirectGraph getOrBuildGraph() {
		if(graph == null && root != null) {
			FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
			graph = flatthelper.buildDirectGraph(root);
		}
		return graph;
	}
	
}
