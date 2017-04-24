package pkg;

import java.util.concurrent.TimeUnit;

/**
 * This illustrates a bug in fernflower as of 20170421. Decompiled output of this class does not compile back.
 */
public class TestSwitchOnEnum {

  int myInt;

  public int testSOE(TimeUnit t) {
    // This creates anonymous SwitchMap inner class.
    switch (t) {
      case SECONDS:
        return 1;
    }
    return 0;
  }
}
