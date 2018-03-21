// "Replace 'if else' with '?:'" "INFORMATION"
class Test {
  String foo(String currentBranch) {
      // comment
      return currentBranch.isEmpty() ? currentBranch : currentBranch.substring(0);
  }
}