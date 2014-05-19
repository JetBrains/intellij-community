package test.util;

import java.util.HashMap;

public class Timer {

	private static HashMap<String, Double> mapValue = new HashMap<String, Double>(); 
	
	public static void addTime(int index, long value) {
		addTime(String.valueOf(index), value);
	}

	public static double getTime(int index) {
		return mapValue.get(String.valueOf(index));
	}

	public static void addTime(String index, double value) {
		Double val = mapValue.get(index);
		if(val != null) {
			value+=val.doubleValue();
		}  
		mapValue.put(index, value);
	}

	public static double getTime(String index) {
		Double value = mapValue.get(index);
		return value==null?0:value.doubleValue();
	}
	
}

