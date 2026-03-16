class Y {
  
  void x() {
    <warning descr="println"><caret>System.out.println();</warning>
    <warning descr="println">System.out.println();</warning>
    <warning descr="println">System.out.println();</warning>
    
    // noinspection println
    System.out.println();
  }
}