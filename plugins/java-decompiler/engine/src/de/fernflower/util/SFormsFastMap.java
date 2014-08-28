package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;

public class SFormsFastMap<E> {

	private int size;
	
	private List<E[]> lstElements = new ArrayList<E[]>(3);
	
	{
		lstElements.add((E[])new Object[5]);
		lstElements.add((E[])new Object[5]);
		lstElements.add((E[])new Object[5]);
	}

	public SFormsFastMap() {}
	
	public SFormsFastMap(SFormsFastMap<E> map) {
		for(int i=2;i>=0;i--) {
			E[] arr = map.lstElements.get(i);
			E[] arrnew = (E[])new Object[arr.length];
			
			System.arraycopy(arr, 0, arrnew, 0, arr.length);
			
			lstElements.set(i,  arrnew);
			
			for(E element : arrnew) {
				if(element != null) {
					size++;
				}
			}
		}
		
	}
	
	public int size() {
		return size;
	}
	
	public boolean isEmpty() {
		return size == 0;
	}
	
	public void put(int key, E value) {
		putInternal(key, value, false);
	}
	
	public void remove(int key) {
		putInternal(key, null, true);
	}
	
	public void removeAllFields() {
		E[] arr = lstElements.get(2);
		for(int i=arr.length-1;i>=0;i--) {
			E val = arr[i];
			if(val != null) {
				arr[i] = null;
				size--;
			}
		}
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
		
		E[] arr = lstElements.get(index);
		if(ikey >= arr.length) {
			if(remove) {
				return;
			} else {
				arr = ensureCapacity(arr, ikey+1, false);
				lstElements.set(index, arr);
			}
		}
		
		E oldval = arr[ikey];
		arr[ikey] = value;
		
		if(oldval == null && value != null) {
			size++;
		} else if(oldval != null && value == null) {
			size--;
		}
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
		
		E[] arr = lstElements.get(index);
		
		if(key < arr.length) {
			return arr[key];
		}
		return null;
	}
	
	public void union(SFormsFastMap<E> map, IElementsUnion<E> union) {
		
		for(int i=2;i>=0;i--) {
			E[] lstOwn = lstElements.get(i);
			E[] lstExtern = map.lstElements.get(i);

			int externsize = lstExtern.length;
			
			if(lstOwn.length<externsize) {
				lstOwn = ensureCapacity(lstOwn, externsize, true);
				lstElements.set(i, lstOwn);
			}
			
			int ownsize = lstOwn.length;
			int minsize = ownsize>externsize?externsize:ownsize;
			
			for(int j=minsize-1;j>=0;j--) {
				E second = lstExtern[j];

				if(second != null) {
					E first = lstOwn[j];

					if(first == null) {
						lstOwn[j] = union.copy(second);
						size++;
					} else {
						union.union(first, second);
					}
				}
			}
			
//	        ITimer timer = TimerFactory.newTimer();
//	        timer.start();
//
//			if(externsize > minsize) {
//				for(int j=minsize;j<externsize;j++) {
//					E second = lstExtern.get(j);
//					if(second != null) {
//						lstOwn.add(union.copy(second));
//						size++;
//					} else {
//						lstOwn.add(null);
//					}
//				}
//			}
//			
//	        timer.stop();
//			Timer.addTime("sformunion", timer.getDuration());
			
		}
		
	}
	
	public List<Entry<Integer, E>> entryList() {
		List<Entry<Integer, E>> list = new ArrayList<Entry<Integer, E>>();
		
		for(int i=2;i>=0;i--) {
			int ikey = 0;
			for(final E ent : lstElements.get(i)) {
				if(ent != null) {
					final int key = i==0?ikey:(i==1?ikey+VarExprent.STACK_BASE:-ikey);
					
					list.add(new Entry<Integer, E>() {
						
						private Integer var = key;
						private E val = ent;
						
						public Integer getKey() {
							return var;
						}
			
						public E getValue() {
							return val;
						}
			
						public E setValue(E newvalue) {
							return null;
						}
					});
				}
				
				ikey++;
			}
		}
		
		return list;
	}
	
	private E[] ensureCapacity(E[] arr, int size, boolean exact) {
		
		int minsize = size;
		if(!exact) {
			minsize = 2*arr.length/3 +1;
			if(size > minsize) {
				minsize = size;
			}
		}
		
		E[] arrnew = (E[])new Object[minsize];
		System.arraycopy(arr, 0, arrnew, 0, arr.length);
		
		return arrnew;
	}
	
	public static interface IElementsUnion<E> {
		public E union(E first, E second);
		public E copy(E element);
	}
	
}
