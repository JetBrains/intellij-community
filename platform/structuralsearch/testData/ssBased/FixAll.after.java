class Y {
  
  void x() {
    <caret>System.out.print('\n');
    System.out.print('\n');
    System.out.print('\n');
    
    // noinspection println
    System.out.println();
  }
}