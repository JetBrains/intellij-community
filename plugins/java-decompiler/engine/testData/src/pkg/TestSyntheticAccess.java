package pkg;

class TestSyntheticAccess {

  private static int s;
  private int i;

  private class Incrementer {
    void orI() {
      i|=1;
    }

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
