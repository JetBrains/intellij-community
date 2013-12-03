import org.junit.Assert;

class Simple {

  public static final int EXPECTED = -9;

  private class BeanCreator {

    public BeanCreator  withQuery(int s) {
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(s, -1);
      Assert.<warning descr="Arguments to 'assertSame()' in wrong order">assertSame</warning>(s, EXPECTED);
      junit.framework.Assert.<warning descr="Arguments to 'failNotEquals()' in wrong order">failNotEquals</warning>("asdfasd", s, EXPECTED);
      return null;
    }
  }
}