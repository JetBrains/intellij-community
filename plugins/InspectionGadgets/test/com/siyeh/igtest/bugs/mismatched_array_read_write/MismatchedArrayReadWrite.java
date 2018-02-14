package com.siyeh.igtest.bugs.mismatched_array_read_write;

import java.util.*;

import static java.lang.System.arraycopy;

public class MismatchedArrayReadWrite {
    private int[] <warning descr="Contents of array 'foo' are written to, but never read">foo</warning> = new int[3];
    private int[] <warning descr="Contents of array 'bar' are read, but never written to">bar</warning>;
    private int[] IVAL_3 = {3}; // strictly written, but warned about by "Unused declaration" inspection

    public void bar()
    {
        foo[2] = bar[2];
    }

    public void bar2()
    {
        final int[] <warning descr="Contents of array 'barzoom' are written to, but never read">barzoom</warning> = new int[3];
        barzoom[2] = 3;
    }

    public void bar3()
    {
        final int[] <warning descr="Contents of array 'barzoom' are read, but never written to">barzoom</warning> = new int[3];
        int baz = barzoom[2];
    }

    public int[] bar4()
    {
        final int[] barzoom = new int[3];
        return barzoom;
    }

    public int bar5()
    {
        final int[] <warning descr="Contents of array 'barzoom' are read, but never written to">barzoom</warning> = new int[3];
        return barzoom[3];
    }

    public void bar6()
    {
        final int[] <warning descr="Contents of array 'barzoom' are read, but never written to">barzoom</warning> = new int[3];
        System.out.println(barzoom[3]);
    }

    public void bar7()
    {
        final int[] barzoom = new int[3];
        System.out.println(barzoom);
    }

    public void bar8() {
        int[][] array = new int[10][10];
        for (int i = 9; i >= 0; i--) {
            for (int j = 9; j >= 0; j--) {
                array[i][j] = 20;
            }
        }
        System.out.println(array[0][0]);
    }

    public void bar9() {
        final int[][] allResults = new int[10][10];
        for (int row = 0; row < allResults.length; row++) {
            final int[] rowData = allResults[row];
            for (int col = 0; col < 10; col++) {
                rowData[col] = 100 * 7;

            }
        }
    }

    void bar10(int[] is) {
        int[] <warning descr="Contents of array 'js' are written to, but never read">js</warning> = new int[is.length];
        arraycopy(is, 0, js, 0, is.length);
        int[] <warning descr="Contents of array 'ks' are read, but never written to">ks</warning> = new int[is.length];
        arraycopy(ks, 0, is, 0, is.length);
    }

    public void bar11() {
        final int[][] <warning descr="Contents of array 'allResults' are written to, but never read">allResults</warning> = new int[10][10];
        for (int row = 0; row < 10; row++) {
            allResults[row] = new int[9];
        }
    }
}
class Test{
    public void bar(){
        int[] a = new int[5];
        for(int i = 0; i < a.length; i++){
            a[i] = i;
        }
        final java.lang.Object[] o = new java.lang.Object[]{a};
        foo(o);
    }

    public void foo(java.lang.Object[] o){

    }

    private void testStuff() {
        int[][] array = new int[2][2];
        array[0][1]++;
    }

    void foo1() {
        final int[] <warning descr="Contents of array 'barzoom' are written to, but never read">barzoom</warning> = {};
        barzoom[2] = 3;
    }

    void foo2() {
        final int[] <warning descr="Contents of array 'barzoom' are written to, but never read">barzoom</warning> = new int[]{};
        barzoom[2] = 3;
    }

    void foo3(Object[] otherArr) {
        Object[] <warning descr="Contents of array 'arr' are written to, but never read">arr</warning> = otherArr.clone();
        for (int i = 0; i < 10; i++) arr[i] = i;
    }

    void foo4() {
        int[] array = {1, 2, 4};
        assert array.length == 3;
    }

  private String[] getAttrInfos() {
    return null;
  }
  private String mon() {
    String[] attributeInfo = getAttrInfos();
    if (attributeInfo != null) {
      System.out.println("");
    }
    return null;
  }
}
class Bug {
  // Example 1
  public void test1() {
    final java.util.List<long[]> results = new java.util.ArrayList<>();
    for (int i = 0; i < 10; i++) {
      results.add(new long[3]);
    }

    for (int i = 0; i < results.size(); i++) {
      final long[] longs = results.get(i); // <-- Contents of array 'longs' are written to, but never read
      for (int j = 0; j < 3; j++) {
        longs[j] = i * j;
      }
    }

    for (long[] result : results) {
      for (long l : result) {
        System.out.println(l);
      }
    }
  }

  // Example 2
  private int[] _ints = {0};

  public void print() {
    for (int i : _ints) {
      System.out.println(i);
    }
  }

  public void test2() {
    final Bug bug = new Bug();
    final int[] ints = bug._ints; // <-- Contents of array 'ints' are written to, but never read
    ints[0] = 1;
    bug.print();
  }
}
class Toster
{
  private static final int MAX = 1;

  public static void main(String[] args)
  {
    new Toster().run();
  }

  private int[][] values = new int[][] { {new java.util.Random().nextInt()}, {new java.util.Random().nextInt()} };

  private void run()
  {
    for (int j = new java.util.Random().nextInt(values.length); j < new java.util.Random().nextInt(values.length); j++)
    {
      int[] array = values[j]; //IDEA says here: contents of array 'array' are written to, but never used

      for (int i = 0; i < MAX; i++)
      {
        array[i] = new java.util.Random().nextInt();
      }
    }
  }
}
class Ferrari458Spider implements java.io.Serializable {
  private static final java.io.ObjectStreamField[] serialPersistentFields = {
    new java.io.ObjectStreamField("b", String.class)
  };
}

class TestIDEA128098 {
  private static final String[] CONSTANT_ARRAY = new String[]{""}; // warning is on this constant


  enum SomeEnum {
    ITEM( CONSTANT_ARRAY);
    private final String[] myPatterns;
    SomeEnum(String... patterns) {
      myPatterns = patterns;
    }
  }

  void m(boolean b, String[] ts) {
    Object[] <warning descr="Contents of array 'ss' are read, but never written to">ss</warning> = new String[10];
    for (String s : b ? (String[])(ss) : ts) {

    }
  }

  void m1(List<String> list) {
    String[] ss = list.toArray(new String[0]);
    System.out.println(ss[0]);
  }

  void m2(Object[] otherArr) {
    Object[] <warning descr="Contents of array 'arr' are written to, but never read">arr</warning> = new Object[10];
    Arrays.fill(arr, "null");
  }

  void m3() {
    String[] <warning descr="Contents of array 'ss' are read, but never written to">ss</warning> = new String[1];
    System.out.println(Arrays.toString(ss));
  }
}
class Ball {
  private String[] names;

  Ball() {
    names = new String[] {
      "tennisball",
      "football",
      "basketball"
    };
  }

  public String toString() {
    return Arrays.toString(names);
  }
}
class ParentRecorder {
  private final int[] parents;

  public ParentRecorder(int[] parents) {
    this.parents = parents;
    Arrays.fill(parents, -1);
  }

  public void treeEdge(int e, int g) {
    parents[e] = g;
  }
}
class Example {
  private final int[] reducedToFull;
  Example() {
    reducedToFull = new int[]{1, 2, 4};
    reducedToFull[2] = 3;
  }
  void method(String[] fullOrders, String[] reducedOrders) {
    for (int j = 0; j < reducedOrders.length; j++) {
      fullOrders[reducedToFull[j]] = reducedOrders[j];
    }
  }
}
class Example2 {
  private final int[] <warning descr="Contents of array 'is' are written to, but never read">is</warning>;
  Example2() {
    is = new int[] {36, 42, 12};
    is[1] = 27;
  }
}