package p1.p2;

public class IdsClass {
  public void f() {
    int n = R.id.myIdFromDrawable;
    n = R.id.<error>myIdFromDrawable1</error>;
  }
}
