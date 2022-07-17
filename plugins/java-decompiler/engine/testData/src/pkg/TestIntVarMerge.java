package pkg;

public class TestIntVarMerge {
  public int test1() {
    int hash = 7;
    hash = 23 * hash;
    hash *= 23;
    return hash;
  }

  public void test2() {
    int k = 3;
    System.out.println(k);
    k++;
    System.out.println(k);
    ++k;
    System.out.println(k);
  }
}
