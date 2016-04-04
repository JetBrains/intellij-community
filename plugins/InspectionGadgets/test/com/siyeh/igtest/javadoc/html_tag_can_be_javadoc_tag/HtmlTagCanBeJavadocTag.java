package com.siyeh.igtest.javadoc.html_tag_can_be_javadoc_tag;

class HtmlTagCanBeJavadocTag {

  /**
   * <warning descr="'<code>...</code>' can be replaced with '{@code ...}'"><code></warning>if (something) { this.doSomething(); }</code>
   * <warning descr="'<code>...</code>' can be replaced with '{@code ...}'"><code></warning>
   *     asdf
   * </code>
   * <warning descr="'<code>...</code>' can be replaced with '{@code ...}'"><code></warning></code><warning descr="'<code>...</code>' can be replaced with '{@code ...}'"><code></warning></code>
   */
  void foo() {}

  /**
   * <warning descr="'<CODE>...</code>' can be replaced with '{@code ...}'"><CODE></warning>HEAVY CODE</CODE>
   */
  void bar() {}

  /**
   * <code>if (foo) {<br>  System.out.println();<br>}</code>
   */
  void extremeFormatting() {}

  /**
   * Demo value.
   */
  public int x;

  /**
   * Another demo value.
   *
   * <code>x2 = {@link #x x} * 2</code>
   */
  public int x2 = x*2;
}