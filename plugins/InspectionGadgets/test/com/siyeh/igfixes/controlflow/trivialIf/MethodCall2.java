class Main {
  private static void printInt(int x, int y) {
    //
  }

  public static void main(int x, int y) {
    <caret>if (x == y) {
      printInt(x, y);
    } else {
      printInt(y, y);
    }
  }
}