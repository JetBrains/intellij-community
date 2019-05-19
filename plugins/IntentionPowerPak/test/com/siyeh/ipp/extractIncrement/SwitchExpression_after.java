class Main {
  int test(int i) {
    return switch(i) {
      default -> {
          ++i;
          break i;
      }
    };
  }
}