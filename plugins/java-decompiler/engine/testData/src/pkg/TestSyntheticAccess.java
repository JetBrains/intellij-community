package pkg;

/**
 * @author Alexandru-Constantin Bledea
 * @since March 20, 2016
 */
class TestSyntheticAccess {

  private static int s;
  private int i;

  private class Incrementer {
    void incrementI() {
      i++;
    }

    void decrementI() {
      --i;
    }

    void incrementS() {
      ++s;
    }

    void decrementS() {
      s--;
    }
  }

  private class Assigner {
    void assignI(int newValue) {
      i = newValue;
    }

    void assignS(int newValue) {
      s = newValue;
    }
  }

}
