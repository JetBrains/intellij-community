class Main {
  enum X {A, B}

  boolean test(X c1, X c2) {
    return switch(0) {
      default -> c1.<caret>equals(c2);
    };
  }
}