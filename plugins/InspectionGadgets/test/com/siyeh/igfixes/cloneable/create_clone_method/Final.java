class Child<caret> extends Parent implements Cloneable {

}
class Parent implements Cloneable {
  public final Parent clone() {
    try {
      return (Parent)super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }
}