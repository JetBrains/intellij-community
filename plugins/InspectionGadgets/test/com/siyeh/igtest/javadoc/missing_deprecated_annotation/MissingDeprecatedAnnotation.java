/**
 * @deprecated
 */
public class <warning descr="Missing '@Deprecated' annotation">MissingDeprecatedAnnotation</warning> {

  /**
   * @deprecated Use {@link #b()} instead
   */
  @Deprecated
  void a() {}

  /**
   * @deprecated
   */
  void <warning descr="Missing '@Deprecated' annotation">b</warning>() {}

  /**
   * @deprecated
   */
  String <warning descr="Missing '@Deprecated' annotation">s</warning>;

}
@Deprecated
class <warning descr="Missing '@deprecated' Javadoc tag explanation">Two</warning> {

  /**
   * @deprecated
   */
  @Deprecated
  void <warning descr="Missing '@deprecated' Javadoc tag explanation">a</warning>() {}

  @Deprecated
  void <warning descr="Missing '@deprecated' Javadoc tag explanation">b</warning>() {}

  @Deprecated
  String <warning descr="Missing '@deprecated' Javadoc tag explanation">s</warning>;
}
class Parent {
  /** @deprecated don't use */
  @Deprecated
  public void some() {
  }
}

class Child extends Parent {
  @Deprecated
  @Override
  public void some() {
    super.some();
  }
}