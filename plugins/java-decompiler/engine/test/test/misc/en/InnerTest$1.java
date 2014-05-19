package test.misc.en;


class InnerTest$1 extends Foo implements Runnable {

   // $FF: synthetic field
   final String val$test;
   // $FF: synthetic field
   final int val$test1;


   InnerTest$1(String var1, int var2) {
      super();
      this.val$test = var1;
      this.val$test1 = var2;
   }

   public void run() {
      System.out.println(this.val$test);
      System.out.println(this.val$test1);
   }
}
