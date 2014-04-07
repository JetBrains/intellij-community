package com.siyeh.igtest.numeric.pointless_arithmetic_expression;

public class PointlessArithmeticExpression
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

    double boom(double d){
        return 1.1 * d;
    }

    void doubleDoom(int i) {

        if (i > Integer.MAX_VALUE) {
            System.out.println("always false!");
        }
        if (i < Integer.MAX_VALUE) {
            System.out.println("do nothing");
        }
        if (i <= Integer.MAX_VALUE) {
            System.out.println("always true");
        }

        if (i >= Integer.MIN_VALUE) {
            System.out.println("sometimes possible");
        }
        if (i < Integer.MIN_VALUE) {
            System.out.println("always false");
        }
    }

    double floatsOrDoubles() {
        return 123.001 % 1.0;
    }

    void more(int i) {
        System.out.println(i / i);
        System.out.println(i - i);
        System.out.println(i % i);
    }
}
class Main {
  private static final int CONST = 9;
  int i;
  Main(int i) {
    this.i = i;
  }

  static int doo() {
    return new Main(1).i - new Main(0).i;
  }

  int fly(int i) {
    final Main main = new Main(12);
    return (CONST + (new Main(5).i) * 8) - (Main.CONST + new Main(5).i * (8));
  }

  int one = 5/5;
}
class Expanded {{
  int m = 1/**/ - (byte)0 - 9; // warn
  int j = 8 * 0 * 8;
  int k = 1 + /*a*/0 +/**/ 9;
  byte l = (byte) (1L - 1L);
  byte u = 1;
  int z = 2 / 1 / 1;
  System.out.println(u * 1);
  long g = 8L / 8L;
  long h = 9L * 0L;
}}