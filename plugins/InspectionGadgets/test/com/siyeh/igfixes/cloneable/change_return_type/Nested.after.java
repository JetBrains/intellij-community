class Nested implements Cloneable {

  public Nested clone() throws CloneNotSupportedException {
    new Object() {
      Object x() {
        return null;
      }
    }
      /*1*/
      return (Nested) super.clone();
  }
}