package com.siyeh.igtest.javadoc.html_tag_can_be_javadoc_tag;

class HtmlTagCanBeJavadocTag {

  /**
   * <code>if (something) { this.doSomething(); }</code>
   * <code>
   *     asdf
   * </code>
   * <code></code><code></code>
   */
  void foo() {}
}