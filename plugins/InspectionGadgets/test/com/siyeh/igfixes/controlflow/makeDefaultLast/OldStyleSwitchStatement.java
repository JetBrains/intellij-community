class MyTest {
  void m(int i) {
    int j = switch(i) {
      def<caret>ault:
         System.out.println();
         break 4;//comment
      case 2: 
        System.out.println();
        break 45;
    }
  }
}