package com.intellij.grazie.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NaturalTextDetectorTest {
  @Test
  public void test() {
    assertTrue(NaturalTextDetector.seemsNatural("The dog barks"));
    assertTrue(NaturalTextDetector.seemsNatural("This is the greatest setting of all: you can turn it on/off without any trouble"));
    assertTrue(NaturalTextDetector.seemsNatural("two dog"));

    assertFalse(NaturalTextDetector.seemsNatural("someSpringBean"));
    assertFalse(NaturalTextDetector.seemsNatural("Accept: text/plain"));
  }
}