package com.siyeh.igtest.verbose;

public class PointlessArithmeticExpressionInspection
{
    private static final int ZERO_CONSTANT = 0;
    private static final int ONE_CONSTANT = 1;

    public static void main(String[] args)
    {
        final int i = 2;
        final int j = i + 0;
        System.out.println(j);
        int k = 0+j;
        System.out.println(k);
         k = j - 0;
        System.out.println(k);
        k = 0 - j;
        System.out.println(k);
        k = j * ZERO_CONSTANT;
        System.out.println(k);
        k = j * ONE_CONSTANT;
        System.out.println(k);
        k = j / 1;
        System.out.println(k);
        String string = "foo" + 0;

        k = j%1;
        System.out.println(k);
               
        if(k<=Integer.MAX_VALUE)
        {

        }
        if(k>=Integer.MIN_VALUE)
        {

        }
        if(k>Integer.MAX_VALUE)
        {

        }
        if(k<Integer.MIN_VALUE)
        {

        }
        if(Integer.MAX_VALUE >= k)
        {

        }
        if(Integer.MIN_VALUE <= k)
        {

        }
        if(Integer.MAX_VALUE < k)
        {

        }
        if(Integer.MIN_VALUE > k)
        {

        }



    }
}
