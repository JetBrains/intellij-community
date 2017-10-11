class PublicNoThrows extends Parent {

    public PublicNoThrows clone() throws AssertionError {
        throw new AssertionError();
    }
}
class Parent implements Cloneable {

  public Parent clone() {
    retur null;
  }
}