/**
 *
 * Test case based on sample code from <a href="https://youtrack.jetbrains.com/issue/IDEA-247575">IDEA-247575</a>.
 * File should be compiled with <code>-g:none -parameters</code> javac flags.
 *
 */
package pkg;

public class TestMethodParametersAttrStaticNoDebugInfo {

  public static void foo(int x, int y) {
    int z = x + y;
    System.out.println(z);
  }

}