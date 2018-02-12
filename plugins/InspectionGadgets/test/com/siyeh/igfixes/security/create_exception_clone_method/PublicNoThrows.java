class PublicNoThrows<caret> extends Parent {

}
class Parent implements Cloneable {

  public Parent clone() {
    retur null;
  }
}