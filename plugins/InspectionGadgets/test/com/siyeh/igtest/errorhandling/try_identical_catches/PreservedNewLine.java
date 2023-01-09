public class PreservedNewLine {
  void m() {
    try {
    }
    catch (NullPointerException e) {}
    <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (ArrayIndexOutOfBoundsException <caret>e)</warning> {}
    catch (ArrayStoreException e) {
      System.out.println(1);
    }
  }
}
