package pkg;

class PrivateClasses {
  private interface Callable<T> {
    T call();
  }

  private static final Runnable R1 = new Runnable() {
    @Override
    public void run() {
      String s = "";

      class NonCapturingLocalR1 {
        private final String s;

        public NonCapturingLocalR1(String s) {
          this.s = s;
        }

        @Override
        public String toString() {
          return this.s;
        }
      }

      class CapturingLocalR1 {
        private final int i;

        public CapturingLocalR1(int i) {
          this.i = i;
        }

        @Override
        public String toString() {
          return s + ":" + i;
        }
      }

      new NonCapturingLocalR1(s).toString();
      new CapturingLocalR1(42).toString();

      Callable<String> c1 = new Callable<String>() {
        @Override
        public String call() {
          return null;
        }
      };

      Callable<String> c2 = new Callable<String>() {
        @Override
        public String call() {
          return s;
        }
      };

      (c1.call() + c2.call()).length();
    }
  };

  private final Runnable R2 = new Runnable() {
    @Override
    public void run() {
      String s = "";

      class NonCapturingLocalR2 {
        private final String s;

        public NonCapturingLocalR2(String s) {
          this.s = s;
        }

        @Override
        public String toString() {
          return this.s;
        }
      }

      class CapturingLocalR1 {
        private final int i;

        public CapturingLocalR1(int i) {
          this.i = i;
        }

        @Override
        public String toString() {
          return s + ":" + i;
        }
      }

      new NonCapturingLocalR2(s).toString();
      new CapturingLocalR1(42).toString();

      Callable<String> c1 = new Callable<String>() {
        @Override
        public String call() {
          return null;
        }
      };

      Callable<String> c2 = new Callable<String>() {
        @Override
        public String call() {
          return s;
        }
      };

      (c1.call() + c2.call()).length();
    }
  };

  public static void m1(String s) {
    class NonCapturingLocalM1 {
      private final String s;

      public NonCapturingLocalM1(String s) {
        this.s = s;
      }

      @Override
      public String toString() {
        return this.s;
      }
    }

    class CapturingLocalM1 {
      private final int i;

      public CapturingLocalM1(int i) {
        this.i = i;
      }

      @Override
      public String toString() {
        return s + ":" + i;
      }
    }

    new NonCapturingLocalM1(s).toString();
    new CapturingLocalM1(42).toString();

    Callable<String> c1 = new Callable<String>() {
      @Override
      public String call() {
        return null;
      }
    };

    Callable<String> c2 = new Callable<String>() {
      @Override
      public String call() {
        return s;
      }
    };

    (c1.call() + c2.call()).length();
  }

  public void m2(String s) {
    class NonCapturingLocalM2 {
      private final String s;

      public NonCapturingLocalM2(String s) {
        this.s = s;
      }

      @Override
      public String toString() {
        return this.s;
      }
    }

    class CapturingLocalM2 {
      private final int i;

      public CapturingLocalM2(int i) {
        this.i = i;
      }

      @Override
      public String toString() {
        return s + ":" + i;
      }
    }

    new NonCapturingLocalM2(s).toString();
    new CapturingLocalM2(42).toString();

    Callable<String> c1 = new Callable<String>() {
      @Override
      public String call() {
        return null;
      }
    };

    Callable<String> c2 = new Callable<String>() {
      @Override
      public String call() {
        return s;
      }
    };

    (c1.call() + c2.call()).length();
  }
}