package com.siyeh.igtest.bugs.mismatched_array_read_write;

public class MismatchedArrayReadWrite {
    private int[] foo = new int[3];
    private int[] bar;

    public void bar()
    {
        foo[2] = bar[2];
    }

    public void bar2()
    {
        final int[] barzoom = new int[3];
        barzoom[2] = 3;
    }

    public void bar3()
    {
        final int[] barzoom = new int[3];
        int baz = barzoom[2];
    }

    public int[] bar4()
    {
        final int[] barzoom = new int[3];
        return barzoom;
    }

    public int bar5()
    {
        final int[] barzoom = new int[3];
        return barzoom[3];
    }

    public void bar6()
    {
        final int[] barzoom = new int[3];
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
            for (int col = 0; col < rowData.length; col++) {
                rowData[col] = 100 * 7;

            }
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
        System.out.println(array[0][1]);
    }

    void foo1() {
        final int[] barzoom = {};
        barzoom[2] = 3;
    }

    void foo2() {
        final int[] barzoom = new int[]{};
        barzoom[2] = 3;
    }

    void foo3(Object[] otherArr) {
        Object[] arr = otherArr.clone();
        for (int i = 0; i < 10; i++) arr[i] = i;
    }
}
