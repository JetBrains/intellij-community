/**
 * @deprecated
 */
public class MissingDeprecatedAnnotation {

  /**
   * @deprecated Use {@link #b()} instead
   */
  @Deprecated
  void a() {}

  /**
   * @deprecated
   */
  void b() {}

  /**
   * @deprecated
   */
  String s;

}
/**
 * @deprecated TODO: explain
 */
@Deprecated
class Two {

  /**
   * @deprecated
   */
  @Deprecated
  void a() {}

  /**
   * @deprecated TODO: explain
   */
  @Deprecated
  void b() {}

  /**
   * @deprecated TODO: explain
   */
  @Deprecated
  String s;
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
class Debugger {
  /**
   * @deprecated {@link XDebuggerManager#getCurrentSession()} is used instead
   */
  @Deprecated
  public void getCurrentSession() {}

  /**
   * @deprecated use {@link #CONTENT_ROOT_ICON_CLOSED}
   */
  @Deprecated String CONTENT_ROOT_ICON_OPEN = null;
}
