package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;

public class SFormsFastMapOld<E> {

	private List<ArrayList<Entry<Integer, E>>> lstElements = new ArrayList<ArrayList<Entry<Integer, E>>>(3);
	
	{
		lstElements.add(new ArrayList<Entry<Integer, E>>());
		lstElements.add(new ArrayList<Entry<Integer, E>>());
		lstElements.add(new ArrayList<Entry<Integer, E>>());
	}

	public SFormsFastMapOld() {}
	
	public SFormsFastMapOld(SFormsFastMapOld<E> map) {
		for(int i=2;i>=0;i--) {
			lstElements.set(i, new ArrayList<Entry<Integer, E>>(map.lstElements.get(i))); 
		}
	}
	
	public int size() {
		int size = 0;
		for(int i=2;i>=0;i--) {
			size += lstElements.get(i).size();
		}
		return size;
	}
	
	public boolean isEmpty() {
		
		for(int i=2;i>=0;i--) {
			if(!lstElements.get(i).isEmpty()) {
				return false;
			}
		}
		
		return true;
	}
	
	public void put(int key, E value) {
		putInternal(key, value, false);
	}
	
	public void remove(int key) {
		putInternal(key, null, true);
	}
	
	public void removeAllFields() {
		lstElements.get(2).clear();
	}
	
	public void putInternal(final int key, final E value, boolean remove) {
		
		int index = 0;
		int ikey = key; 
		if(ikey < 0) {
			index = 2;
			ikey = -ikey;
		} else if(ikey >= VarExprent.STACK_BASE) {
			index = 1;
			ikey -= VarExprent.STACK_BASE;
		}
		
		ArrayList<Entry<Integer, E>> lst = lstElements.get(index);
		if(ikey >= lst.size()) {
			if(remove) {
				return;
			} else {
				ensureCapacity(lst, ikey+1, false);
			}
		}
		
		lst.set(ikey, value==null?null:new Entry<Integer, E>() {

			private Integer var = key;
			private E val = value;
			
			public Integer getKey() {
				return var;
			}

			public E getValue() {
				return val;
			}

			public E setValue(E newvalue) {
				val = newvalue;
				return null;
			}});
	}
	
	public boolean containsKey(int key) {
		return get(key) != null;
	}
	
	public E get(int key) {
		
		int index = 0;
		if(key < 0) {
			index = 2;
			key = -key;
		} else if(key >= VarExprent.STACK_BASE) {
			index = 1;
			key -= VarExprent.STACK_BASE;
		}
		
		ArrayList<Entry<Integer, E>> lst = lstElements.get(index);
		
		Entry<Integer, E> ent;
		if(key < lst.size() && (ent = lst.get(key)) != null) {
			return ent.getValue();
		}
		return null;
	}
	
	public void union(SFormsFastMapOld<E> map, IElementsUnion<E> union) {
		
		for(int i=2;i>=0;i--) {
			ArrayList<Entry<Integer, E>> lstOwn = lstElements.get(i);
			ArrayList<Entry<Integer, E>> lstExtern = map.lstElements.get(i);

			int ownsize = lstOwn.size();
			int externsize = lstExtern.size();
			
			int minsize = ownsize>externsize?externsize:ownsize;
			
			for(int j=minsize-1;j>=0;j--) {
				Entry<Integer, E> second = lstExtern.get(j);

				if(second != null) {
					Entry<Integer, E> first = lstOwn.get(j);

					if(first == null) {
						putInternal(second.getKey(), union.copy(second.getValue()), false);
					} else {
						first.setValue(union.union(first.getValue(), second.getValue()));
					}
				}
			}
			
			if(externsize > minsize) {
//				ensureCapacity(lstOwn, externsize, true);
//				lstOwn.addAll(lstExtern.subList(minsize, externsize));
				
				for(int j=minsize;j<externsize;j++) {
					Entry<Integer, E> second = lstExtern.get(j);
//					if(first != null) {
//						first.setValue(union.copy(first.getValue()));
//					}
					
					if(second != null) {
						putInternal(second.getKey(), union.copy(second.getValue()), false);
					}
//					lstOwn.add(lstExtern.get(j));
				}
			}
		}
		
	}
	
	public List<Entry<Integer, E>> entryList() {
		List<Entry<Integer, E>> list = new ArrayList<Entry<Integer, E>>();
		
		for(int i=2;i>=0;i--) {
			for(Entry<Integer, E> ent : lstElements.get(i)) {
				if(ent != null) {
					list.add(ent);
				}
			}
		}
		
		return list;
	}
	
//	public SFormsFastMapIterator iterator() {
//		return new SFormsFastMapIterator();
//	}
		
	private void ensureCapacity(ArrayList<Entry<Integer, E>> lst, int size, boolean exact) {
		
		if(!exact) {
			int minsize = 2*lst.size()/3 +1;
			if(minsize > size) {
				size = minsize;
			}
		}
		
		lst.ensureCapacity(size);
		for(int i=size-lst.size();i>0;i--) {
			lst.add(null);
		}
	}
	
	public static interface IElementsUnion<E> {
		public E union(E first, E second);
		public E copy(E element);
	}
	
//	public class SFormsFastMapIterator implements Iterator<Entry<Integer, E>> {
//
//		private int[] pointer = new int[]{0, -1};
//		private int[] next_pointer = null;
//		
//		private int[] getNextIndex(int list, int index) {
//
//			while(list < 3) {
//				ArrayList<E> lst = lstElements.get(list);
//				
//				while(++index < lst.size()) {
//					E element = lst.get(index);
//					if(element != null) {
//						return new int[] {list, index};
//					}
//				}
//				
//				index = -1;
//				list++;
//			}
//
//			return null;
//		}
//		
//		public boolean hasNext() {
//			next_pointer = getNextIndex(pointer[0], pointer[1]); 
//			return (next_pointer != null);
//		}
//
//		public Entry<Integer, E> next() {
//			if(next_pointer != null) {
//				pointer = next_pointer;
//			} else {
//				int[] nextpointer = getNextIndex(pointer[0], pointer[1]);
//				if(nextpointer != null) {
//					pointer = nextpointer; 
//				} else {
//					return null;
//				}
//			}
//			
//			next_pointer = null;
//			
//			return new Entry<Integer, E>() {
//				public Integer getKey() {
//					return null;
//				}
//
//				public E getValue() {
//					return null;
//				}
//
//				public E setValue(E value) {
//					throw new RuntimeException("not implemented!");
//				}
//			};
//			//lstElements.get(pointer[0]).get(pointer[1]);
//		}
//
//		public void remove() {
//			throw new RuntimeException("not implemented!");
//		}
//		
//	}
	
}
