package pkg;

public class TestClassFields {
  private static class Inner {
    private static int staticMutable;
  }

  private static int[] sizes;
  private static String[] names;

  private static final int SIZE;

  static {
    names = new String[]{"name1", "name2"};
    sizes = new int[names.length];

    Inner.staticMutable = 3;
    SIZE = Inner.staticMutable;
  }
}