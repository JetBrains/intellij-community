class DefaultMethods implements Something {

  @java.lang.Override
  public void något() {

  }

  @java.lang.Override
  public void iets() {

  }

  @java.lang.Override
  public void etwas() {

  }
}
interface Something {

  default void något() {
    System.out.println("1");
  }

  default void iets() {
    System.out.println(2);
  }

  default void etwas() {
    System.out.println(0x3);
  }
}