class NoThrows extends Parent implements Cloneable {

    public NoThrows clone() {
        return (NoThrows) super.clone();
    }
}
class Parent implements Cloneable {

  public Parent clone() {
    try {
      return (Parent) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError()
    }
  }
}