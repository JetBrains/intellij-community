package pkg;

import java.util.concurrent.TimeUnit;

public class TestSwitchOnEnum {

  int myInt;// dummy field is required to trigger NPE in attempt to determine whether
            // any field name matches the anon inner class name

  public int testSOE(TimeUnit t) {
    // This creates anonymous SwitchMap inner class.
    switch (t) {
      case SECONDS:
        return 1;
    }
    return 0;
  }
}