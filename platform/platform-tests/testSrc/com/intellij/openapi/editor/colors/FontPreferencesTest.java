/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors;


import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FontPreferencesTest {
  private final FontPreferencesImpl myPreferences = new FontPreferencesImpl();

  @Test
  public void testDefaults() {
    checkState(Collections.emptyList(),
               Collections.emptyList(),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, null);
  }

  @Test
  public void testRegisterExistingFont() {
    String fontName = getExistingNonDefaultFontName();
    myPreferences.register(fontName, 25);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, 25);
  }

  @Test
  public void testRegisterTwoExistingFonts() {
    String fontName = getExistingNonDefaultFontName();
    myPreferences.register(fontName, 25);
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myPreferences.register(fontName2, 13);
    checkState(Arrays.asList(fontName, fontName2),
               Arrays.asList(fontName, fontName2),
               fontName,
               fontName, 25,
               fontName2, 13);
  }

  @Test
  public void testRegisterNonExistingFont() {
    String fontName = getNonExistingFontName();
    myPreferences.register(fontName, 25);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, 25);
  }

  @Test
  public void testAddExistingFont() {
    String fontName = getExistingNonDefaultFontName();
    myPreferences.addFontFamily(fontName);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, null);
  }

  @Test
  public void testAddNonExistingFont() {
    String fontName = getNonExistingFontName();
    myPreferences.addFontFamily(fontName);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, null);
  }

  @Test
  public void testCharacterVariantsAreUnmodifiableSnapshot() {
    myPreferences.setCharacterVariant("zero", true);
    Set<String> variants = myPreferences.getCharacterVariants();
    myPreferences.setCharacterVariant("ss01", true);
    assertEquals(Set.of("zero"), variants);
    assertEquals(Set.of("zero", "ss01"), myPreferences.getCharacterVariants());
    //noinspection DataFlowIssue
    assertThrows(UnsupportedOperationException.class, () -> variants.add("cv01"));
  }

  @Test
  public void testSetCharacterVariantsCopiesTheSet() {
    Set<String> source = new HashSet<>(Set.of("zero"));
    myPreferences.setCharacterVariants(source);
    source.add("ss01");
    assertEquals(Set.of("zero"), myPreferences.getCharacterVariants());
  }

  @Test
  public void testSetCharacterVariantsDropsNullElement() {
    Set<String> withNull = new HashSet<>();
    withNull.add("zero");
    withNull.add(null);
    myPreferences.setCharacterVariants(withNull);
    assertEquals(Set.of("zero"), myPreferences.getCharacterVariants());
  }

  @Test
  public void testCopyToCopiesCharacterVariants() {
    myPreferences.setCharacterVariants(Set.of("zero", "ss01"));
    FontPreferencesImpl copy = new FontPreferencesImpl();
    myPreferences.copyTo(copy);
    assertEquals(Set.of("zero", "ss01"), copy.getCharacterVariants());
  }

  @Test
  public void testCopyToDropsSizeOfFontFamilyThatIsNotReal() {
    myPreferences.register("Primary", 20);
    myPreferences.setFontSize("Unregistered", 30);
    var copy = new FontPreferencesImpl();
    myPreferences.copyTo(copy);
    assertTrue(copy.hasSize("Primary"));
    assertFalse(copy.hasSize("Unregistered"));
  }

  @Test
  public void testCopyToFiresOneEventAndNoEventForSameSnapshot() {
    myPreferences.register("Primary", 20);
    myPreferences.setUseLigatures(true);
    myPreferences.setRegularSubFamily("Light");
    myPreferences.setCharacterVariants(Set.of("zero"));
    var copy = new FontPreferencesImpl();
    var events = new AtomicInteger();
    copy.addChangeListener(_ -> events.incrementAndGet());

    myPreferences.copyTo(copy);
    assertEquals(1, events.get());

    myPreferences.copyTo(copy);
    assertEquals(1, events.get());
  }

  @Test
  public void testCopyToSubclassCallsOverriddenSetter() {
    myPreferences.register("Primary", 20);
    var templateFontSizes = new ArrayList<Float>();
    var subclass = new FontPreferencesImpl() {
      @Override
      public void setTemplateFontSize(float size) {
        templateFontSizes.add(size);
        super.setTemplateFontSize(size);
      }
    };

    myPreferences.copyTo(subclass);

    assertEquals(List.of(20f), templateFontSizes);
    checkState(subclass, List.of("Primary"), List.of("Primary"), "Primary", "Primary", 20);
  }

  @Test
  public void testBatchUpdateFiresOneEvent() {
    var events = new AtomicInteger();
    myPreferences.addChangeListener(_ -> events.incrementAndGet());

    myPreferences.runBatchUpdate(draft -> {
      draft.clear();
      draft.register("Primary", 20);
      draft.setUseLigatures(true);
      draft.setRegularSubFamily("Light");
    });

    assertEquals(1, events.get());
    checkState(myPreferences, List.of("Primary"), List.of("Primary"), "Primary", "Primary", 20);
    assertTrue(myPreferences.useLigatures());
    assertEquals("Light", myPreferences.getRegularSubFamily());
  }

  @Test
  public void testBatchUpdateWritesNothingWhenChangesThrow() {
    myPreferences.register("Primary", 20);
    var events = new AtomicInteger();
    myPreferences.addChangeListener(_ -> events.incrementAndGet());

    assertThrows(IllegalStateException.class, () -> myPreferences.runBatchUpdate(draft -> {
      draft.clear();
      throw new IllegalStateException();
    }));

    assertEquals(0, events.get());
    checkState(myPreferences, List.of("Primary"), List.of("Primary"), "Primary", "Primary", 20);
  }

  @Test
  public void testBatchUpdateGetterSeesEarlierChange() {
    myPreferences.runBatchUpdate(draft -> {
      draft.register("Primary", 20);
      draft.setTemplateFontSize(draft.getSize2D("Primary") + 1);
    });

    assertEquals(21, myPreferences.getSize2D("Other"), 0.001f);
  }

  @Test
  public void testBatchUpdateOnSubclassRunsChangesOnInstance() {
    var templateFontSizes = new ArrayList<Float>();
    var subclass = new FontPreferencesImpl() {
      @Override
      public void setTemplateFontSize(float size) {
        templateFontSizes.add(size);
        super.setTemplateFontSize(size);
      }
    };

    subclass.runBatchUpdate(draft -> draft.setTemplateFontSize(20f));

    assertEquals(List.of(20f), templateFontSizes);
  }

  @Test
  public void testBatchUpdateRunsChangesAgainAfterConcurrentChange() {
    var runs = new AtomicInteger();

    myPreferences.runBatchUpdate(draft -> {
      if (runs.getAndIncrement() == 0) {
        // Simulates a write of another thread between the read of the snapshot and the compare-and-set
        myPreferences.setLineSpacing(2f);
      }
      draft.register("Primary", 20);
    });

    assertEquals(2, runs.get());
    assertEquals(2f, myPreferences.getLineSpacing(), 0.001f);
    checkState(myPreferences, List.of("Primary"), List.of("Primary"), "Primary", "Primary", 20);
  }

  @Test
  public void testSnapshotDoesNotChangeAfterLaterChange() {
    myPreferences.register("Primary", 20);
    var before = myPreferences.getSnapshot();
    assertSame(before, myPreferences.getSnapshot());

    myPreferences.register("Secondary", 30);

    checkState(before, List.of("Primary"), List.of("Primary"), "Primary", "Primary", 20);
    assertFalse(before instanceof ModifiableFontPreferences);
  }

  @Test
  public void testRegisterAndAddOfPresentFontFamilyKeepSnapshot() {
    myPreferences.register("Primary", 20);
    var before = myPreferences.getSnapshot();

    myPreferences.register("Primary", 20);
    myPreferences.addFontFamily("Primary");

    assertSame(before, myPreferences.getSnapshot());
  }

  @Test
  public void testSnapshotOfBasePreferencesIsThis() {
    var preferences = new FontPreferences();
    assertSame(preferences, preferences.getSnapshot());
  }

  @Test
  public void testSnapshotOfDelegatingPreferencesIsSnapshotOfDelegate() {
    myPreferences.register("Primary", 20);
    var delegating = new DelegatingFontPreferences(() -> myPreferences);
    var before = delegating.getSnapshot();
    assertSame(myPreferences.getSnapshot(), before);

    myPreferences.register("Secondary", 30);

    checkState(before, List.of("Primary"), List.of("Primary"), "Primary", "Primary", 20);
    assertSame(myPreferences.getSnapshot(), delegating.getSnapshot());
  }

  @Test
  public void testBatchUpdateGivesSameResultAsSetters() {
    assertBatchUpdateGivesSameResultAsSetters(_ -> {}, FontPreferencesTest::changeEverything);
  }

  @Test
  public void testBatchUpdateGivesSameResultAsSettersForClearAndReset() {
    assertBatchUpdateGivesSameResultAsSetters(FontPreferencesTest::changeEverything, preferences -> {
      preferences.clearFonts();
      preferences.register("Third", 14);
      preferences.resetFontSizes();
      preferences.setTemplateFontSize(15);
      preferences.addFontFamily("Fourth");
    });
    assertBatchUpdateGivesSameResultAsSetters(FontPreferencesTest::changeEverything, preferences -> {
      preferences.clear();
      preferences.addFontFamily("Third");
    });
  }

  @Test
  public void testBatchUpdateWithSameValuesKeepsSnapshotAndFiresNoEvent() {
    myPreferences.register("Primary", 20);
    var before = myPreferences.getSnapshot();
    var events = new AtomicInteger();
    myPreferences.addChangeListener(_ -> events.incrementAndGet());

    myPreferences.runBatchUpdate(draft -> {
      draft.clear();
      draft.register("Primary", 20);
    });

    assertSame(before, myPreferences.getSnapshot());
    assertEquals(0, events.get());
  }

  @Test
  public void testEventFiresExactlyWhenSnapshotChanges() {
    var events = new AtomicInteger();
    myPreferences.addChangeListener(_ -> events.incrementAndGet());

    myPreferences.clear();
    assertEquals(0, events.get());

    myPreferences.setLineSpacing(1.5f);
    assertEquals(1, events.get());
    myPreferences.setLineSpacing(1.5f);
    assertEquals(1, events.get());

    myPreferences.register("Primary", 20);
    assertEquals(2, events.get());
    myPreferences.register("Primary", 20);
    myPreferences.addFontFamily("Primary");
    myPreferences.setCharacterVariants(Set.of());
    myPreferences.setCharacterVariant("zero", false);
    assertEquals(2, events.get());
  }

  @Test
  public void testReaderOnOtherThreadNeverSeesHalfDoneBatch() throws InterruptedException {
    myPreferences.register("Primary", 10);
    var stop = new AtomicBoolean();
    var halfDone = new AtomicInteger();
    var reader = new Thread(() -> {
      while (!stop.get()) {
        var current = myPreferences.getSnapshot();
        if (current.getEffectiveFontFamilies().isEmpty() || !current.hasSize("Primary")) {
          halfDone.incrementAndGet();
        }
      }
    }, "FontPreferencesTest reader");
    reader.start();
    try {
      for (int i = 0; i < 100_000; i++) {
        float size = 10 + i % 2;
        myPreferences.runBatchUpdate(draft -> {
          draft.clear();
          draft.register("Primary", size);
        });
      }
    }
    finally {
      stop.set(true);
      reader.join();
    }

    assertEquals(0, halfDone.get());
  }

  private static final List<String> PROBED_FONT_FAMILIES =
    List.of("Primary", "Secondary", "Fallback", "Effective", "Unregistered", "Third", "Fourth", "Other");

  private static void changeEverything(ModifiableFontPreferences preferences) {
    preferences.register("Primary", 13.5f);
    preferences.register("Secondary", 12);
    preferences.addFontFamily("Fallback");
    preferences.setFontSize("Fallback", 10.5f);
    preferences.setFontSize("Unregistered", 9);
    preferences.setTemplateFontSize(11.5f);
    preferences.setLineSpacing(1.5f);
    preferences.setUseLigatures(true);
    preferences.setRegularSubFamily("Light");
    preferences.setBoldSubFamily("SemiBold");
    preferences.setCharacterVariants(Set.of("zero"));
    preferences.setCharacterVariant("ss01", true);
    preferences.setCharacterVariant("zero", false);
    preferences.setEffectiveFontFamilies(List.of("Primary", "Fallback", "Effective"));
    preferences.setRealFontFamilies(List.of("Primary", "Fallback"));
  }

  /**
   * Applies the base changes to two new instances, then the changes with the setters to one and with {@code runBatchUpdate} to the other.
   */
  private static void assertBatchUpdateGivesSameResultAsSetters(Consumer<ModifiableFontPreferences> base,
                                                           Consumer<ModifiableFontPreferences> changes) {
    var bySetters = new FontPreferencesImpl();
    base.accept(bySetters);
    changes.accept(bySetters);

    var byBatchUpdate = new FontPreferencesImpl();
    base.accept(byBatchUpdate);
    byBatchUpdate.runBatchUpdate(changes);

    assertEquals(bySetters.getEffectiveFontFamilies(), byBatchUpdate.getEffectiveFontFamilies());
    assertEquals(bySetters.getRealFontFamilies(), byBatchUpdate.getRealFontFamilies());
    assertEquals(bySetters.getFontFamily(), byBatchUpdate.getFontFamily());
    for (String fontFamily : PROBED_FONT_FAMILIES) {
      assertEquals(fontFamily, bySetters.hasSize(fontFamily), byBatchUpdate.hasSize(fontFamily));
      assertEquals(fontFamily, bySetters.getSize2D(fontFamily), byBatchUpdate.getSize2D(fontFamily), 0f);
    }
    assertEquals(bySetters.getLineSpacing(), byBatchUpdate.getLineSpacing(), 0f);
    assertEquals(bySetters.useLigatures(), byBatchUpdate.useLigatures());
    assertEquals(bySetters.getRegularSubFamily(), byBatchUpdate.getRegularSubFamily());
    assertEquals(bySetters.getBoldSubFamily(), byBatchUpdate.getBoldSubFamily());
    assertEquals(bySetters.getCharacterVariants(), byBatchUpdate.getCharacterVariants());
  }

  private void checkState(java.util.List<String> expectedRealFontFamilies,
                          java.util.List<String> expectedEffectiveFontFamilies,
                          String expectedFontFamily,
                          Object... namesAndSizes) {
    checkState(myPreferences,
               expectedRealFontFamilies,
               expectedEffectiveFontFamilies,
               expectedFontFamily,
               namesAndSizes);

    // check object copying
    FontPreferencesImpl preferences = new FontPreferencesImpl();
    myPreferences.copyTo(preferences);
    // check myTemplateFontSize
    String fontName = "Another" + getNonExistingFontName();
    assertEquals(myPreferences.getSize(fontName), preferences.getSize(fontName));
    // check other fields
    checkState(myPreferences,
               preferences.getRealFontFamilies(),
               preferences.getEffectiveFontFamilies(),
               preferences.getFontFamily());
  }

  public static void checkState(FontPreferences fontPreferences,
                          java.util.List<String> expectedRealFontFamilies,
                          java.util.List<String> expectedEffectiveFontFamilies,
                          String expectedFontFamily,
                          Object... namesAndSizes) {
    assertEquals("Wrong real font families", expectedRealFontFamilies, fontPreferences.getRealFontFamilies());
    assertEquals("Wrong effective font families", expectedEffectiveFontFamilies, fontPreferences.getEffectiveFontFamilies());
    assertEquals("Wrong font family", expectedFontFamily, fontPreferences.getFontFamily());
    for (int i = 0; i < namesAndSizes.length - 1; ) {
      String fontName = (String)namesAndSizes[i++];
      Number fontSize = (Number)namesAndSizes[i++];
      assertEquals("Wrong hasSize", fontSize != null, fontPreferences.hasSize(fontName));
      assertEquals("Wrong font size", fontSize == null ? (float)FontPreferences.DEFAULT_FONT_SIZE : fontSize.floatValue(), fontPreferences.getSize2D(fontName), 0.001f);
    }
  }

  public static String getExistingNonDefaultFontName() {
    return getExistingFontNameButNot(FontPreferences.DEFAULT_FONT_NAME);
  }

  public static String getAnotherExistingNonDefaultFontName() {
    String firstOne = getExistingFontNameButNot(FontPreferences.DEFAULT_FONT_NAME);
    return getExistingFontNameButNot(FontPreferences.DEFAULT_FONT_NAME, firstOne);
  }

  private static String getExistingFontNameButNot(String... names) {
    String[] fontFamilyNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    for (String name : fontFamilyNames) {
      if (name != null && !Arrays.asList(names).contains(name)) {
        return name;
      }
    }
    fail("Couldn't find existing font not in " + Arrays.toString(names));
    return null;
  }

  public static String getNonExistingFontName() {
    return "DefinitelyNonExistingFontName";
  }
}
