package pkg;

// compile with `javac -g ...`
class TestDebugSymbols {
  private int m() {
    String text = "text";
    long prolonged = 42L;
    float decimated = prolonged / 10.0f;
    double doubled = 2 * decimated;
    return (text + ":" + prolonged + ":" + decimated + ":" + doubled).length();
  }

  public void test() {
    int i = 0;
    int count = 0;
    do {
      i += count++;
    } while( i < 10 );
  }
}
