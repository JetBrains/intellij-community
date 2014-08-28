package test.misc.en;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class ListInsertTest {

	public static void main(String[] args) {
		
		List<Integer> lst1 = new ArrayList<Integer>(Arrays.asList(new Integer[]{1, 2, 3}));
		List<Integer> lst2 = new LinkedList<Integer>(Arrays.asList(new Integer[]{1, 2, 3}));

		Date d = new Date();
		
		for(int i=0;i<300000;i++) {
			lst1.add(1, i);
		}
		
		System.out.println(new Date().getTime() - d.getTime());

		d = new Date();
		
		for(int i=0;i<300000;i++) {
			lst2.add(1, i);
		}
		
		System.out.println(new Date().getTime() - d.getTime());
		
	}

}
