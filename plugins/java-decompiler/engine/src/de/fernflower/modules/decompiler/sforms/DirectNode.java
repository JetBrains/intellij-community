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

package org.jetbrains.java.decompiler.modules.decompiler.sforms;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;


public class DirectNode {
	
	public static final int NODE_DIRECT = 1;
	public static final int NODE_TAIL = 2;
	public static final int NODE_INIT = 3;
	public static final int NODE_CONDITION = 4;
	public static final int NODE_INCREMENT = 5;
	public static final int NODE_TRY = 6;
	
	public int type;
	
	public String id;
	
	public BasicBlockStatement block;
	
	public Statement statement;
	
	public List<Exprent> exprents = new ArrayList<Exprent>();  
	
	public List<DirectNode> succs = new ArrayList<DirectNode>();
	
	public List<DirectNode> preds = new ArrayList<DirectNode>();
	
	public DirectNode(int type, Statement statement, String id) {
		this.type = type;
		this.statement = statement;
		this.id = id;
	}

	public DirectNode(int type, Statement statement, BasicBlockStatement block) {
		this.type = type;
		this.statement = statement;

		this.id = block.id.toString();
		this.block = block;
	}

	@Override
	public String toString() {
		return id;
	}
	
	
}
