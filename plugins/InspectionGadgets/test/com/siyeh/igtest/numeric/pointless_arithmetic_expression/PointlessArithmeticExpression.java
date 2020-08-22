package com.siyeh.igtest.numeric.pointless_arithmetic_expression;

import java.util.Random;

public class PointlessArithmeticExpression
{
    private static final int ZERO_CONSTANT = 0;
    private static final int ONE_CONSTANT = 1;

    public static void main(String[] args)
    {
        final int i = 2;
        final int j = <warning descr="'i + 0' can be replaced with 'i'">i + 0</warning>;
        System.out.println(j);
        int k = <warning descr="'0+j' can be replaced with 'j'">0+j</warning>;
        System.out.println(k);
         k = <warning descr="'j - 0' can be replaced with 'j'">j - 0</warning>;
        System.out.println(k);
        k = <warning descr="'0 - j' can be replaced with '- j'">0 - j</warning>;
        System.out.println(k);
        k = j * ZERO_CONSTANT;
        System.out.println(k);
        k = j * ONE_CONSTANT;
        System.out.println(k);
        k = <warning descr="'j / 1' can be replaced with 'j'">j / 1</warning>;
        k = 0/0; // don't warn on division by zero
        System.out.println(k);
        String string = "foo" + 0;

        k = <warning descr="'j%1' can be replaced with '0'">j%1</warning>;
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
        System.out.println(<warning descr="'i / i' can be replaced with '1'">i / i</warning>);
        System.out.println(<warning descr="'i - i' can be replaced with '0'">i - i</warning>);
        System.out.println(<warning descr="'i % i' can be replaced with '0'">i % i</warning>);
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

  int one = <warning descr="'5/5' can be replaced with '1'">5/5</warning>;

  void foo(int x) {
    int a = <warning descr="'x - x - 10' can be replaced with '- 10'">x - x - 10</warning>;
    int b = 10 - x - x;
    int c = <warning descr="'x - 10 - x' can be replaced with '- 10'">x - 10 - x</warning>;
  }
}
class Expanded {{
  int m = <warning descr="'1/**/ - (byte)0 - 9' can be replaced with '1 - 9'">1/**/ - (byte)0 - 9</warning>; // warn
  int j = <warning descr="'8 * 0 * 8' can be replaced with '0'">8 * 0 * 8</warning>;
  int k = <warning descr="'1 + /*a*/0 +/**/ 9' can be replaced with '1 + 9'">1 + /*a*/0 +/**/ 9</warning>;
  byte l = (byte) (<warning descr="'1L - 1L' can be replaced with '0L'">1L - 1L</warning>);
  byte u = 1;
  int z = <warning descr="'2 / 1 / 1' can be replaced with '2'">2 / 1 / 1</warning>;
  System.out.println(<warning descr="'u * 1' can be replaced with 'u'">u * 1</warning>);
  long g = <warning descr="'8L / 8L' can be replaced with '1L'">8L / 8L</warning>;
  long h = <warning descr="'9L * 0L' can be replaced with '0L'">9L * 0L</warning>;
  int a = 8 * 0 * 8 *<error descr="Expression expected"> </error>; // don't warn
  int minus = 2 - 1 - 1;
  int div = 3 / 2 / 2;
  int mod = 3 % 2 % 2;

  long typePromotion = <warning descr="'1L * Integer.MAX_VALUE * Integer.MAX_VALUE' can be replaced with 'Integer.MAX_VALUE * Integer.MAX_VALUE'">1L * Integer.MAX_VALUE * Integer.MAX_VALUE</warning>;
}}
class SideEffects {
  public static void main( String args[] ){
    Random rand = new Random();
    int array[] = {1, 2, 4};
    int i = 1;
    int b = array[i++] - array[i++];
    System.out.println(rand.nextInt(1000) - rand.nextInt(1000));
  }
}
class FloatingPoint {
  // Zero processing can be tricky. See IDEA-20768 for details.
  void test(double x) {
    double res = <warning descr="'x - x' can be replaced with '0.0'">x - x</warning>;
    double res1 = <warning descr="'x / x' can be replaced with '1.0'">x / x</warning>;
    double res2 = <warning descr="'x % x' can be replaced with '0.0'">x % x</warning>;
    double res3 = x + 0;
    double res4 = x - 0;
    double res5 = <warning descr="'x * 1.0' can be replaced with 'x'">x * 1.0</warning>;
    double res6 = <warning descr="'x / 1.0' can be replaced with 'x'">x / 1.0</warning>;
    double res7 = x * 0.0;
    double res8 = 0.0 / x;
    double res9 = x % 1.0;
    double nan = 0.0 / 0.0;
  }

  void test2(float x) {
    float res = <warning descr="'x - x' can be replaced with '0.0f'">x - x</warning>;
    float res1 = <warning descr="'x / x' can be replaced with '1.0f'">x / x</warning>;
    float res2 = <warning descr="'x % x' can be replaced with '0.0f'">x % x</warning>;
    float res3 = x + 0;
    float res4 = x - 0;
    float res5 = <warning descr="'x * 1.0f' can be replaced with 'x'">x * 1.0f</warning>;
    float res6 = <warning descr="'x / 1.0f' can be replaced with 'x'">x / 1.0f</warning>;
    float res7 = x * 0.0f;
    float res8 = 0.0f / x;
    float nan = 0.0f / 0.0f;
  }
  
  void multiplyToCast(int x, int y) {
    double xyz = (1.0 * x) / y;
  }
}