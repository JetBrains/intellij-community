// "Fix all 'If statement can be replaced with ?:, && or || expression' problems in file" "false"
class Test {

  boolean test(boolean x) {
    <caret>if (()) {
      return true;
    }
    return x;
  }

}