package test.misc.en;

import java.util.HashMap;

public class AutocastTest {

	public static void main(String[] args) {
		
		HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
		
		Integer key = new Integer(1);
		Integer value = map.containsKey(key)?0:map.get(key);
		
		System.out.println(value == null);
	}
	
}
