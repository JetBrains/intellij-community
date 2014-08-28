package org.jetbrains.java.decompiler.modules.renamer;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.struct.StructClass;

public class ClassWrapperNode {

	private StructClass classStruct;
	
	private ClassWrapperNode superclass;
	
	private List<ClassWrapperNode> subclasses = new ArrayList<ClassWrapperNode>();
	
	public ClassWrapperNode(StructClass cl) {
		this.classStruct = cl;
	}
	
	public void addSubclass(ClassWrapperNode node) {
		node.setSuperclass(this);
		subclasses.add(node);
	}

	public StructClass getClassStruct() {
		return classStruct;
	}

	public List<ClassWrapperNode> getSubclasses() {
		return subclasses;
	}

	public ClassWrapperNode getSuperclass() {
		return superclass;
	}

	public void setSuperclass(ClassWrapperNode superclass) {
		this.superclass = superclass;
	}

}
