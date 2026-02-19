package com.intellij.grazie.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalTextDetectorTest {
  @Test
  public void test() {
    assertTrue(NaturalTextDetector.seemsNatural("The dog barks"));
    assertTrue(NaturalTextDetector.seemsNatural("This is the greatest setting of all: you can turn it on/off without any trouble"));
    assertTrue(NaturalTextDetector.seemsNatural("two dog"));
    assertTrue(NaturalTextDetector.seemsNatural("原子笔"));

    assertFalse(NaturalTextDetector.seemsNatural("someSpringBean"));
    assertFalse(NaturalTextDetector.seemsNatural("Accept: text/plain"));
  }
}