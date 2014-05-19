package test.misc.en;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import de.fernflower.util.FastSetFactory;
import de.fernflower.util.FastSetFactory.FastSet;

public class FastSetTest {

	public static void main(String[] args) {

		SortedSet<Integer> set = new TreeSet<Integer>();   
		
		for(int i=0;i<3;i++) {
			set.add(i);
		}
		
//		for(Integer s : set) {
//			System.out.println(s);
//		}
		
		 FastSetFactory<Integer> factory = new FastSetFactory<Integer>(set);
		
//		 factory.print();
	
//		 int index = 1;
//		 for(int i=0;i<100;i++) {
//			 if(i % 32 == 0) {
//				 index = 1;
//			 }
//
//			 System.out.println(index);
//			 index<<=1;
//			 
//		 }
		 
		 

		 FastSet<Integer> set1 = factory.spawnEmptySet();
		 set1.addAll(new HashSet<Integer>(Arrays.asList(new Integer[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9})));
		 
		 FastSet<Integer> set2 = set1.getCopy();
		 set2.remove(4);
		 set2.remove(5);
		 set2.add(10);
		 
		 set1.symdiff(set2);
		 Set<Integer> set3 = new TreeSet<Integer>(set1.toPlainSet());

		 for(Integer s : set3) {
			 System.out.println(s);
		 }
	}
	

}
