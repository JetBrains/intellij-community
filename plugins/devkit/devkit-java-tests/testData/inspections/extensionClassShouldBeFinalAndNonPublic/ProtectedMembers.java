import java.util.ArrayList;

class <warning descr="Extension class should be final">MyInspection<caret></warning> extends ArrayList<String> {
  protected MyInspection() {}
  protected MyInspection(int i) {}

  @Override
  protected void removeRange(int fromIndex, int toIndex) {}

  protected void canBePrivate() {}
  protected void cannotBePrivate() {}
}

class Test {
  void test() {
    new MyInspection().cannotBePrivate();
  }
}