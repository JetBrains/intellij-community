package java14.assertions;

/**
 * same problem as Assert1 for java14
 */

public class Assert2 {
  public static int i = 1;
  public static int j = 2;
  public static int x = 3;
  public static int y = 4;
  public static int z = 5;
  
  public void testWithNumbers(int i, int j, int k) {
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
  
  public static void main(String[] args) {
    Assert2 a = new Assert2();
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        for (int k = 0; k < 3; k++) {
          System.out.println("testWithNumbers: " + i + " " + j + " " + k);
          a.testWithNumbers(i,j,k);
        }
      }
    }
  }
}
