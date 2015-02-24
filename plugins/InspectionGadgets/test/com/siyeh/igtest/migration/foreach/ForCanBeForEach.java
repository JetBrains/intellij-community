package com.siyeh.igtest.migration.foreach;

import java.util.*;

public class ForCanBeForEach {

    public void foo(int[] is) {
        <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0; i < is.length; i++) {
        }
    }

    public void test(Collection bars){
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(Iterator<List> it = bars.iterator(); it .hasNext();){
            final List bar = it.next();
            bar.size();
        }
    }

    public int foo(){
        final int[] ints = new int[3];
        int total = 0;
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(int i = 0; i < ints.length; i++){
            final int j = ints[i];
            total += j;
        }
        return total;
    }

    public int bar(){
        final int[] ints = new int[3];
        int total = 0;
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(int i = 0; i < ints.length; i++){
            total += ints[i];
        }
        return total;
    }

    public int baz(){
        int total = 0;
        final List ints = new ArrayList();
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(Iterator iterator = ints.iterator(); iterator.hasNext();){
            final Integer value = (Integer) iterator.next();
            total += value.intValue();
        }
        return total;
    }

    public int bazoom(){
        int total = 0;
        final List<Integer> ints = new ArrayList<Integer>();
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(Iterator<Integer> iterator = ints.iterator(); iterator.hasNext();){
            final Integer value = iterator.next();
            total += value.intValue();
        }
        return total;
    }

    public int wildBazoom(){
        int total = 0;
        final List<? extends Integer> ints = new ArrayList<Integer>();
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(Iterator<? extends Integer> iterator = ints.iterator();
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
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(Iterator<Map.Entry<String, Integer>> iterator = m.entrySet()
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
        <warning descr="'for' loop replaceable with 'foreach'">for</warning>(int i = 0; i < inners.length; i++){
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
        <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0; i < (numbers.size()); i++) {
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
        <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = (0); (i < (size)); (i)++) {
            Object o = ls.get(i);
            System.out.println("o = " + o);
        }
    }

    class X extends ArrayList {

        void foo() {
            <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0; i < size(); i++) {
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
        <warning descr="'for' loop replaceable with 'foreach'">for</warning> (ListIterator l = ints.listIterator(); l.hasNext(); ) {
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
        for (ListIterator<String> i = l.listIterator(); i.hasNext(); ) {
            if ("sit".equals(i.next())) {
                i.set("stay");
            }
        }
    }

  void indexedList(List<String> l) {
    <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0, max = l.size(); i < max; i++) {
      System.out.println(l.get(i));
    }
    <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = (0), max = (l.size()); ((max) > (i)); (i)++) {
      System.out.println(l.get(i)); }
    for (int i = 0; i < l.size(); i++) {}
  }

  static class Constants {
    public static final String[] STRINGS = {"one", "two", "three"};
  }
  static class User {{
    String[] strings = Constants.STRINGS;
    <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0, length = strings.length; i < length; i++) { // should warn here
      String s = strings[i];
      System.out.println(s);
    }
  }}

  public void food(int[] is) {
    <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0; is.length > i; i++) {
    }
    for (int i = 0, j = 10; i < is.length; i++) {
    }
  }

  void foo(List<String> l) {
    for (int i = 0, j = 10; i < l.size(); i++) {
      System.out.println(j);
    }
  }

  class XX<T> {
    void m(T[] ts) {
      <warning descr="'for' loop replaceable with 'foreach'">for</warning> (int i = 0; i < ts.length; i++) {
        System.out.println();
      }
    }
  }

  static class WithMethodRefs {
      private static final List<String> STRINGS = new ArrayList<>(Arrays.asList("Hello", "World"));

      public void test3() {
          for (ListIterator<String> stringListIterator = STRINGS.listIterator(); stringListIterator.hasNext(); ) {
              System.out.println(stringListIterator.next());

              List<String> strings2 = new ArrayList<>(Collections.singletonList("!"));

              strings2.forEach(stringListIterator::add);
          }
      }
  }
}
class OuterClass
{
  public static enum UnnecessaryEnumModifier2Inspection {
    Red, Green, Blue;

    private UnnecessaryEnumModifier2Inspection() {
    }
  }
}