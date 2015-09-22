package com.intellij.ide.ui;

import junit.framework.TestCase;

import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;

public final class ColorBlindnessTest extends TestCase {
  public void testProtanopiaDaltonization() {
    testZeroFilter(DaltonizationFilter.forProtanopia(0.0));
    compareFilters(DaltonizationFilter.forProtanopia(1.0), DaltonizationFilter.protanopia);
  }

  public void testDeuteranopiaDaltonization() {
    testZeroFilter(DaltonizationFilter.forDeuteranopia(0.0));
    compareFilters(DaltonizationFilter.forDeuteranopia(1.0), DaltonizationFilter.deuteranopia);
  }

  public void testTritanopiaDaltonization() {
    testZeroFilter(DaltonizationFilter.forTritanopia(0.0));
    compareFilters(DaltonizationFilter.forTritanopia(1.0), DaltonizationFilter.tritanopia);
  }

  public void testProtanopiaSimulation() {
    testZeroFilter(SimulationFilter.forProtanopia(0.0));
    compareFilters(SimulationFilter.forProtanopia(1.0), SimulationFilter.protanopia);
  }

  public void testDeuteranopiaSimulation() {
    testZeroFilter(SimulationFilter.forDeuteranopia(0.0));
    compareFilters(SimulationFilter.forDeuteranopia(1.0), SimulationFilter.deuteranopia);
  }

  public void testTritanopiaSimulation() {
    testZeroFilter(SimulationFilter.forTritanopia(0.0));
    compareFilters(SimulationFilter.forTritanopia(1.0), SimulationFilter.tritanopia);
  }

  public void testAchromatopsiaSimulation() {
    testZeroFilter(SimulationFilter.forAchromatopsia(0.0));
    compareFilters(SimulationFilter.forAchromatopsia(1.0), SimulationFilter.achromatopsia);
  }

  private static void testZeroFilter(ImageFilter filter) {
    RGBImageFilter rgb = (RGBImageFilter)filter;
    for (int i = 0; i < 0x01000000; i++) {
      assertEquals(i, rgb.filterRGB(0, 0, i));
    }
  }

  private static void compareFilters(ImageFilter one, ImageFilter two) {
    RGBImageFilter rgb1 = (RGBImageFilter)one;
    RGBImageFilter rgb2 = (RGBImageFilter)two;
    for (int i = 0; i < 0x01000000; i++) {
      assertEquals(rgb1.filterRGB(0, 0, i), rgb2.filterRGB(0, 0, i));
    }
  }
}
