import java.util.ArrayList;

final class MyInspection extends ArrayList<String> {
  MyInspection() {}
  private MyInspection(int i) {}

  @Override
  protected void removeRange(int fromIndex, int toIndex) {}

  private void canBePrivate() {}
  void cannotBePrivate() {}
}

class Test {
  void test() {
    new MyInspection().cannotBePrivate();
  }
}