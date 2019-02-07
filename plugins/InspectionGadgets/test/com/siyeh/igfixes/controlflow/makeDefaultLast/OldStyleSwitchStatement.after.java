class MyTest {
  void m(int i) {
    int j = switch(i) {
        <caret>case 2: 
        System.out.println();
        break 45;
        default:
            System.out.println();
            break 4;//comment
    }
  }
}