class C {
    void m() throws Exception {
        try (AutoCloseable r1 = null) {
          try {
            System.out.println(r1 + ", " + r1);
          }
        }
    }
}