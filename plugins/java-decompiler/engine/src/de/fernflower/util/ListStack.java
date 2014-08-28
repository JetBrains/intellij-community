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

package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;

public class ListStack<T> extends ArrayList<T> {

	protected int pointer = 0; 
	
	public ListStack(){
		super();
	}

	public ListStack(ArrayList<T> list){
		super(list);
	}
	
	public ListStack<T> clone() {
		ListStack<T> newstack = new ListStack<T>(this);
		newstack.pointer = this.pointer;
		return newstack;
	}
	
	public T push(T item) {
		this.add(item);
		pointer++;
		return item;
	}
	
	public T pop() {
		pointer--;
		T o = this.get(pointer);
		this.remove(pointer);
		return o;
	}

	public T pop(int count) {
		T o = null;
		for(int i=count;i>0;i--) {
			o = this.pop();
		}
		return o;
	}
	
	public void remove() {
		pointer--;
		this.remove(pointer);
	}
	
	public void removeMultiple(int count) {
		while(count>0) {
			pointer--;
			this.remove(pointer);
			count--;
		}
	}
	
	public boolean empty() {
		return (pointer==0); 
	}

	public int getPointer() {
		return pointer;
	}
	
	public T get(int index) {
		return super.get(index);
	}

	public T set(int index, T element) {
		return super.set(index, element);
	}
	
	public T getByOffset(int offset) {
		return this.get(pointer+offset);
	}
	
	public void insertByOffset(int offset, T item) {
		this.add(pointer+offset, item);
		pointer++;
	}
	
	public void clear() {
		super.clear();
		pointer = 0;
	}
	
}
