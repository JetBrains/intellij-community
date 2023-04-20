public class PreservedNewLine {
  void m() {
    try {
    }
    catch (NullPointerException | ArrayIndexOutOfBoundsException e) {}
    catch (ArrayStoreException e) {
      System.out.println(1);
    }
  }
}
