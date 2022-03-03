package com.siyeh.igtest.naming.abstract_class_naming_convention;

import org.junit.jupiter.api.*;

class <warning descr="Test name 'WithNested' doesn't match regex '[A-Z][A-Za-z\d]*Test(s|Case)?|Test[A-Z][A-Za-z\d]*'">WithNested</warning> {
  @Nested
  class WithParent {
    @Test
    public void test1() {}
  }
}

class <warning descr="Test name 'Normal' doesn't match regex '[A-Z][A-Za-z\d]*Test(s|Case)?|Test[A-Z][A-Za-z\d]*'">Normal</warning> {
    @Test
    public void test1() {}
}
