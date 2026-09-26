// PROBLEM: Parameter "abc" is never used

abstract class X {
   fun test(<caret>abc: Int) {
       hashCode();
   }
}
