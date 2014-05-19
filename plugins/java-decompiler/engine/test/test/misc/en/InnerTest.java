package test.misc.en;

import test.misc.en.InnerTest$1;

public class InnerTest {

   public static void main(String[] args) throws Throwable {
      String test = args[0];
      int test1 = Integer.parseInt(args[1]);
      new InnerTest$1(test, test1);
      System.out.println("готово");
   }
}
