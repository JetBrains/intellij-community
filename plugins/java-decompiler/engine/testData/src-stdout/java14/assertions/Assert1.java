package java14.assertions;

/**
 * Fails because java14 .class access:
 * $assertionsDisabled is not found as statically initialized field.
 * It looks like this:
 * $assertionsDisabled = !(class$java14$assertions$Assert1 == null?(class$java14$assertions$Assert1 = class$("java14.assertions.Assert1")):class$java14$assertions$Assert1).desiredAssertionStatus()
 * 
 * isExprentIndependent() returns false because there is a EXPRENT_FIELD in this.
 */

public class Assert1 {
  public static int i = 1;
  public static int j = 2;
  public static int x = 3;
  public static int y = 4;
  public static int z = 5;
  
  
  public static void testWithNumbers(int i, int j, int k) {
    try {
      assert i > 0 || j > 0 || k > 0: "Msg 0";
      if (i < j) {
        assert i != 0 && j != 1 || k != 0: "Msg 1";
        if (i == k) {
          assert k == 2: "Msg 1.1";
        }
      }
      else if (i > j && i == 2) {
        assert i != 1 && k != 0: "Msg 2";
        if (j == 0) {
          assert j > k: "Msg 2.1";
        }
        else {
          assert i < 2: "Msg 2.2";
        }
      }
      else if (k != 1 || i == j) {
        System.out.println("elif2: " + i + " " + j + " " + k);
        assert k != 0: "Msg 3";
        System.out.println(".");
        if (k == 2 && i == 1) {
          System.out.println("..");
          assert j < 0: "Msg 3.1";
          System.out.println("...");
        }
        else {
          System.out.println("....");
          assert k < 0: "Msg 3.2";
          System.out.println(".....");
        }
      }
      else {
        assert !(i > j && i == 1) && k < 0: "Msg 4";
      }
      
      assert false: "Msg final";
    }
    catch (AssertionError e) {
      System.out.println(i + " " + j + " " + k + " got AssertionError: " + e);
    }
  }
  
  //try to force the "or case": if($assertionsDisabled || ...
  public static int foo(Object x) {
    assert (x.toString().equals("x") && i < j) || (i > j && y == i);
    return 1;
  }
  
  public static void main(String[] args) {
    try {
      assert i + y > z -2;
      if (i < 3) {
        System.out.println("1");
        assert i < 2;
        System.out.println("2");
        if (j > -3) {
          System.out.println("3");
          assert z > y;
          System.out.println("4");
        }
        else if (z > j) {
          System.out.println("5");
          assert z > i;
          System.out.println("6");
        }
        else {
          System.out.println("7");
          assert z > i;
          System.out.println("8");
        }
      }
      assert z > i + 2;
      
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          for (int k = 0; k < 3; k++) {
            System.out.println("testWithNumbers: " + i + " " + j + " " + k);
            testWithNumbers(i,j,k);
          }
        }
      }
      
      foo(new Assert1());
    }
    catch (AssertionError e) {
      System.out.println("Got AssertionError: " + e);
    }
  }
}
