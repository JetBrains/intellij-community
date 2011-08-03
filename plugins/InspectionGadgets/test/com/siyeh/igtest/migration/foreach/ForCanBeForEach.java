package com.siyeh.igtest.migration.foreach;

import com.siyeh.igtest.verbose.OuterClass;

import java.util.*;

public class ForCanBeForEach {

    public void foo(int[] is) {
        for (int i = 0; i < is.length; i++) {
        }
    }

    public void test(Collection bars){
        for(Iterator<List> it = bars.iterator(); it .hasNext();){
            final List bar = it.next();
            bar.size();
        }
    }

    public int foo(){
        final int[] ints = new int[3];
        int total = 0;
        for(int i = 0; i < ints.length; i++){
            final int j = ints[i];
            total += j;
        }
        return total;
    }

    public int bar(){
        final int[] ints = new int[3];
        int total = 0;
        for(int i = 0; i < ints.length; i++){
            total += ints[i];
        }
        return total;
    }

    public int baz(){
        int total = 0;
        final List ints = new ArrayList();
        for(Iterator iterator = ints.iterator(); iterator.hasNext();){
            final Integer value = (Integer) iterator.next();
            total += value.intValue();
        }
        return total;
    }

    public int bazoom(){
        int total = 0;
        final List<Integer> ints = new ArrayList<Integer>();
        for(Iterator<Integer> iterator = ints.iterator(); iterator.hasNext();){
            final Integer value = iterator.next();
            total += value.intValue();
        }
        return total;
    }

    public int wildBazoom(){
        int total = 0;
        final List<? extends Integer> ints = new ArrayList<Integer>();
        for(Iterator<? extends Integer> iterator = ints.iterator();
            iterator.hasNext();){
            final Integer value = iterator.next();
            total += value.intValue();
        }
        return total;
    }

    public static String[] getAttributes(){
        final String[] result = new String[3];
        for(int j = 0; j < result.length; j++){
            result[j] = "3"; // can't be foreach
        }
        return result;
    }

    public void test(){
        Map<String, Integer> m = new HashMap<String, Integer>();
        m.put("123", 123);
        m.put("456", 456);
        for(Iterator<Map.Entry<String, Integer>> iterator = m.entrySet()
                .iterator(); iterator.hasNext();){
            Map.Entry<String, Integer> entry = iterator.next();
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }

    public void boom(){
        Map<String, Boolean> map = null;

        final Set<Map.Entry<String, Boolean>> entries = map.entrySet();
        for(Iterator<Map.Entry<String, Boolean>> it = entries.iterator();
            it.hasNext();){
            boolean wouldFit = it.next().getValue();
            if(wouldFit){
                // if it would fit before, it might not now
                it.remove(); // can't be foreach
            }
        }
    }

    public void boom2(){
        OuterClass.UnnecessaryEnumModifier2Inspection[] inners = new OuterClass.UnnecessaryEnumModifier2Inspection[3];
        for(int i = 0; i < inners.length; i++){
            OuterClass.UnnecessaryEnumModifier2Inspection inner = inners[i];
            System.out.println(inner);
        }
    }

    public void boomboom(char prev, char[] indices) {
        for (int i = 0; i < indices.length; i++)
        {
            if (indices[i] > prev)
                indices[i]--; // can't be foreach
        }
    }

    public void didTheyImplementLists(){
        List list = new ArrayList();
        for(int i = 0; i < list.size(); i++){
            list.remove(i);
        }
    }

    public void quickFixBoom(List numbers) {
        for (int i = 0; i < (numbers.size()); i++) {
            System.out.println("numbers[i]: " + numbers.get(i));
        }
    }

    private List<Integer> myPath = new ArrayList<Integer>();
    private Integer[] myArray = new Integer[100];

    public void foo(ForCanBeForEach p) {
        for (int i = 0; i < myPath.size(); i++) {
            if (!myPath.get(i).equals(p.myPath.get(i))) {
            }
        }
    }

    void foo2(ForCanBeForEach p) {
        for (int i = 0; i < myArray.length; i++) {
            if (!myArray[i].equals(p.myArray[i])) {


            }
        }
    }

    void bla(Collection totalDiscounts) {
        for ( Iterator iterator = totalDiscounts.iterator(); iterator.hasNext() ; ) {
            String deliveryDiscount = ( String )iterator.next();
            currentActiveDiscountInIteration(iterator );
        }
    }

    private void currentActiveDiscountInIteration(Iterator iterator) {
    }

    private static Phases getPhases(){
        return null;
    }
    public void t() {
        for (Iterator<String> it = getPhases().iterator(3); it.hasNext();) {
            System.out.println(it.next());
        }
    }

    public class Phases implements Iterable<String> {

        public Iterator<String> iterator() {
            return null;
        }

        public Iterator<String> iterator(int i) {
            return null;
        }
    }

    void sizeInVariable(List ls) {
        int size = ls.size();
        for (int i = (0); (i < (size)); (i)++) {
            Object o = ls.get(i);
            System.out.println("o = " + o);
        }
    }

    class X extends ArrayList {

        void foo() {
            for (int i = 0; i < size(); i++) {
                this.get(i);
            }
        }
    }

    List<String> equations;

    Iterator<String> getNextAfter(String eqp) {
        for(Iterator<String> eqpIter = equations.iterator(); eqpIter.hasNext();)
            if(eqpIter.next() == eqp) return eqpIter;
        throw new AssertionError("equation panel not found");
    }

    int strange() {
        int total = 0;
        final List ints = new ArrayList();
        for (ListIterator l = ints.listIterator(); l.hasNext(); ) {
            System.out.println(l.next());
        }
        return total;
    }

    private static void printIntList(boolean print50) {
        List<Integer> intList = new ArrayList<Integer>(100);
        for (int i = 0; i < 100; i++) {
            intList.add(i);
        }
        int size = intList.size();
        if (print50) {
            size = 50; // size variable is modified here, so foreach not applicable
        }
        for (int i = 0; i < size; i++) {
            System.out.println(intList.get(i));
        }
    }

    void listIteration(List<String> l) {
        for (Iterator<String> i = l.listIterator(); l.hasNext(); ) {
            if ("sit".equals(l.next())) {
                i.set("stay");
            }
        }
    }
}
