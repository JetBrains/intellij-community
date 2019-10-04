class Main {
  int test(int i) {
    return switch(i) {
      default -> {
          int result = i;
          i++;
          yield <caret>result;
      } 
    };
  }
}