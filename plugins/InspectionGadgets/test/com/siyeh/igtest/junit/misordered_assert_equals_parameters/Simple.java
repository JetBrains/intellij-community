import org.junit.Assert;

import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

class Simple {

  public static final int EXPECTED = -9;
  private static final Map<String, Integer> map = new HashMap();

  private class BeanCreator {

    public BeanCreator  withQuery(int s, Map test) {
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(s, -1);
      Assert.<warning descr="Arguments to 'assertSame()' in wrong order">assertSame</warning>(s, EXPECTED);
      junit.framework.Assert.<warning descr="Arguments to 'failNotEquals()' in wrong order">failNotEquals</warning>("asdfasd", s, EXPECTED);

      TimeUnit timeUnit = TimeUnit.HOURS;
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(timeUnit, TimeUnit.HOURS);
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(test, map);

      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>("message", test, null);
      Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>(new X().m(), new Integer(10));
      return null;
    }
  }

  enum TimeUnit {
    HOURS
  }

  class X {
    Integer m() {
      return null;
    }
  }
}