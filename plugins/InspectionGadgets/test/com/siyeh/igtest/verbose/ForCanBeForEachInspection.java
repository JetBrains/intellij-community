package com.siyeh.igtest.verbose;

import java.util.*;

public class ForCanBeForEachInspection {

    public int foo() {
        final int[] ints = new int[3];
        int total = 0;
        for (int i = 0; i < ints.length; i++) {
            final int j = ints[i];
            total += j;
        }
        return total;
    }

    public int bar() {
        final int[] ints = new int[3];
        int total = 0;
        for (int i = 0; i < ints.length; i++) {
            total += ints[i];
        }
        return total;
    }

    public int baz() {
        int total = 0;
        final List ints = new ArrayList();
        for (Iterator iterator = ints.iterator(); iterator.hasNext();) {
            final Integer value = (Integer) iterator.next();
            total += value.intValue();
        }
        return total;
    }

    public int bazoom() {
        int total = 0;
        final List<Integer> ints = new ArrayList<Integer>();
        for (Iterator<Integer> iterator = ints.iterator(); iterator.hasNext();) {
            final Integer value = iterator.next();
            total += value.intValue();
        }
        return total;
    }


    public static String[] getAttributes(){
        final String[] result = new String[3];
        for(int j = 0; j < result.length; j++){
            result[j] = "3";
        }
        return result;
    }


    public void test() {
        Map<String, Integer> m = new HashMap<String, Integer>();
        m.put("123", 123);
        m.put("456", 456);
        for (Iterator<Map.Entry<String, Integer>> iterator = m.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, Integer> entry = iterator.next();
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }

    public void boom()
    {
        Map<String, Boolean> map  = null;

        final Set<Map.Entry<String,Boolean>> entries = map.entrySet();
        for(Iterator<Map.Entry<String, Boolean>> it = entries.iterator();
            it.hasNext();){
            boolean wouldFit = it.next().getValue();
            if(wouldFit){
                // if it would fit before, it might not now
                it.remove();
            }
        }

    }


}
