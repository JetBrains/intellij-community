package pkg;

public class TestInUse {
  public int getInt() {
    return 42;
  }

  protected int reuse() {
    int i = 0, d = 0;
    int result = 0;
    do {
      d = getInt();
      result -= d;
    }
    while (++i < 10);
    return result;
  }
}
