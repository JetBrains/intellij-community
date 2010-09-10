package com.siyeh.igtest.performance.manual_array_copy;

public class ManualArrayCopy
{

    public void fooBar()
    {
        final int[] q = new int[3];
        final int[] a = new int[3];
        for(int i = 0; i < a.length; i++)
            q[i] = a[i];
        for(int i = 0; i < a.length; i++)
            q[i+3] = a[i+4];
        for(int i = 0; i < a.length; i++)
        {
            q[i] = a[i];
        }
        for(int i = 0; i < a.length; i++)
        {
            q[i+3] = a[i];
        }
        for(int i = 1; i < a.length; i++)
        {
            q[2+i] = a[i-1];
        }
        for(int i = 1; i < a.length; i++)
            // not a legal array copy
            q[2-i] = a[i-1];
    }

    void barFoo() {
        int added_index = 3;
        int[] array = new int[10];
        int[] new_array = new int[14];
        for (int i=0;i<array.length;i++) array[i] = i;
        for (int i = 0; i < array.length; i++)
        {
            new_array[added_index + i] = array[i];
        }
        System.out.print("Old Array: ");
        for (int i : array) System.out.print(i+" ");
        System.out.println();
        System.out.print("New Array: ");
        for (int i : new_array) System.out.print(i+" ");
        System.out.println();
    }

    static void foobarred(int[] a, int[] b) {
        int x = 3;
        for(int i = x ; i < a.length; i++) {
            b[i - x] = a[i];
        }
    }

    void boom() {
        byte image[] = new byte[10];
        int data[] = new int[10];
        for (int k = 0; k < 5; ++k) { // breaks if converted to System.arraycopy()
            image[k] = (byte)data[k];
        }
    }

    void boomboom() {
        Object target[] = new Object[10];
        String source[] = new String[10];
        for (int k = 0; k < 5; k++) { // can be converted to System.arraycopy()
            target[k] = source[k];
        }
    }

    static Integer[] nono(int[] ints) {
        Integer[] array = new Integer[ints.length];
        for ( int i = 0; i < ints.length; i++ ) {
            array[i] = ints[i];
        }
        return array;
    }

    void ButItIsWrong(int sp, int kp, int[][] varcov, int[] eval) {
        for (int i = 0; i < sp; i++)
            varcov[i][i] = eval[i + kp];
    }
}
