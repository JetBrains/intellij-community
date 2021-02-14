class MethodMayBeSynchronized {
  public int <warning descr="Method 'generateInt()' with synchronized block could be synchronized method">generateInt<caret></warning>(int x) {
    // 1
    synchronized/*2*/ (/*3*/this/*4*/) { // 5
      // 6
      return 1; // 7
      // 8
    }
  }
}