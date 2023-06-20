class MyTest {
  void m(int i) {
    int j = switch(i) {
      <caret>  case 2: 
        System.out.println();
        yield 45;
        default:
            System.out.println();
            yield 4;//comment
    }
  }
}