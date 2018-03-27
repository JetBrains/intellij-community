class PublicNoThrows extends Parent {

    @Override
    public PublicNoThrows clone() throws AssertionError {
        throw new AssertionError();
    }
}
class Parent implements Cloneable {

  public Parent clone() {
    retur null;
  }
}