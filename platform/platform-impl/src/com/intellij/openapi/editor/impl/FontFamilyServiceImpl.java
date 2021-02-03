// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.MultiMap;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.font.Font2D;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

public final class FontFamilyServiceImpl extends FontFamilyService {
  private static final Logger LOG = Logger.getInstance(FontFamilyServiceImpl.class);

  private static final Method GET_FONT_2D_METHOD = ReflectionUtil.getDeclaredMethod(Font.class, "getFont2D");
  private static final Method GET_TYPO_FAMILY_METHOD = ReflectionUtil.getDeclaredMethod(Font2D.class, "getTypographicFamilyName");
  private static final Method GET_TYPO_SUBFAMILY_METHOD = ReflectionUtil.getDeclaredMethod(Font2D.class, "getTypographicSubfamilyName");
  private static final Method GET_WEIGHT_METHOD = ReflectionUtil.getDeclaredMethod(Font2D.class, "getWeight");

  private static final AffineTransform SYNTHETIC_ITALICS_TRANSFORM = AffineTransform.getShearInstance(-0.2, 0);
  private static final int PREFERRED_MAIN_WEIGHT = 400;
  private static final int PREFERRED_BOLD_WEIGHT_DIFF = 300;

  private final SortedMap<String, FontFamily> myFamilies = new TreeMap<>();

  private FontFamilyServiceImpl() {
    if (AppEditorFontOptions.NEW_FONT_SELECTOR) {
      if (GET_FONT_2D_METHOD == null || GET_TYPO_FAMILY_METHOD == null || GET_TYPO_SUBFAMILY_METHOD == null || GET_WEIGHT_METHOD == null) {
        LOG.warn("Couldn't access required runtime API, will fall back to basic logic of font selection");
      }
      else {
        try {
          Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
          for (Font font : fonts) {
            Font2D font2D = (Font2D)GET_FONT_2D_METHOD.invoke(font);
            String fontName = font.getFontName();
            String font2DName = font2D.getFontName(null);
            if (!fontName.equals(font2DName)) {
              // skip fonts that are declared as available, but cannot be used due to some reason,
              // with JDK substituting them with a different font (on Windows)
              if (LOG.isDebugEnabled()) {
                LOG.debug("Skipping '" + fontName + "' as it's mapped to '" + font2DName + "' by the runtime");
              }
              continue;
            }
            String family = (String)GET_TYPO_FAMILY_METHOD.invoke(font2D);
            String subfamily = (String)GET_TYPO_SUBFAMILY_METHOD.invoke(font2D);
            FontFamily fontFamily = myFamilies.computeIfAbsent(family, FontFamily::new);
            fontFamily.addFont(subfamily, font);
          }
        }
        catch (Throwable e) {
          LOG.error(e);
          myFamilies.clear(); // fallback to old behaviour in case of any errors
        }
      }
    }
  }

  @Override
  protected boolean isSupportedImpl() {
    return !myFamilies.isEmpty();
  }

  @Override
  protected @NotNull List<String> getAvailableFamiliesImpl() {
    return myFamilies.isEmpty() ? super.getAvailableFamiliesImpl() : new ArrayList<>(myFamilies.keySet());
  }

  @Override
  protected boolean isMonospacedImpl(@NotNull String family) {
    FontFamily fontFamily = myFamilies.get(family);
    return fontFamily == null ? super.isMonospacedImpl(family) : fontFamily.isMonospaced();
  }

  @Override
  protected @NotNull List<@NotNull String> getSubFamiliesImpl(@NotNull String family) {
    FontFamily fontFamily = myFamilies.get(family);
    return fontFamily == null ? super.getSubFamiliesImpl(family) : fontFamily.getBaseSubFamilies();
  }

  @Override
  protected @NotNull String getRecommendedSubFamilyImpl(@NotNull String family) {
    FontFamily fontFamily = myFamilies.get(family);
    return fontFamily == null ? super.getRecommendedSubFamilyImpl(family) : fontFamily.getRecommendedSubFamily();
  }

  @Override
  protected @NotNull String getRecommendedBoldSubFamilyImpl(@NotNull String family, @NotNull String mainSubFamily) {
    FontFamily fontFamily = myFamilies.get(family);
    return fontFamily == null ? super.getRecommendedBoldSubFamilyImpl(family, mainSubFamily)
                              : fontFamily.getRecommendedBoldSubFamily(mainSubFamily);
  }

  @Override
  protected @NotNull Font getFontImpl(@NotNull String family,
                                      @Nullable String regularSubFamily,
                                      @Nullable String boldSubFamily,
                                      @JdkConstants.FontStyle int style) {
    FontFamily fontFamily = myFamilies.get(family);
    return fontFamily == null ? super.getFontImpl(family, regularSubFamily, boldSubFamily, style)
                              : fontFamily.getFont(regularSubFamily, boldSubFamily, style);
  }

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static final class FontFamily {
    private final String family;
    private final Map<String, Font> members = new LinkedHashMap<>();

    private String recommendedSubFamily;
    private Map<String, String> recommendedBoldSubFamilies;
    private Map<String, String> recommendedPlainSubFamilies;
    private Map<String, Font> italics;

    private FontFamily(@NotNull String family) {this.family = family;}

    private void addFont(@NotNull String subFamily, @NotNull Font font) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Adding " + font.getName() + " as " + subFamily + " variant to " + family + " family");
      }
      members.put(subFamily, font);
    }

    private synchronized void initIfNeeded() {
      if (recommendedSubFamily == null) {
        recommendedPlainSubFamilies = new HashMap<>();
        recommendedBoldSubFamilies = new HashMap<>();
        italics = new LinkedHashMap<>();
        OurWeightMap nonItalicsByWeight = new OurWeightMap();
        OurWeightMap italicsByWeight = new OurWeightMap();
        for (Map.Entry<String, Font> e : members.entrySet()) {
          String subFamily = e.getKey();
          Font font = e.getValue();
          int weight = getWeight(font);
          boolean isItalic = isItalic(subFamily);
          if (LOG.isDebugEnabled()) {
            LOG.debug(family + "(" + subFamily + "): weight=" + weight + (isItalic ? ", italic" : ""));
          }
          (isItalic ? italicsByWeight : nonItalicsByWeight).putValue(weight, subFamily);
        }
        OurWeightMap baseSet = nonItalicsByWeight.isEmpty() ? italicsByWeight : nonItalicsByWeight;

        class Candidate {
          final int desiredWeight;
          String bestSubFamily;
          int bestDistance = Integer.MAX_VALUE;

          private Candidate(String defaultSubFamily, int desiredWeight) {
            bestSubFamily = defaultSubFamily;
            this.desiredWeight = desiredWeight;
          }

          private void updateIfBetterMatch(int weight, String subFamily) {
            int newMetric = Math.abs(desiredWeight - weight);
            if (newMetric < bestDistance) {
              bestDistance = newMetric;
              bestSubFamily = subFamily;
            }
          }
        }

        Candidate preferred = new Candidate(null, PREFERRED_MAIN_WEIGHT);
        Map<String, Candidate> candidates = new HashMap<>();
        baseSet.forEach((weight, subFamily) -> {
          preferred.updateIfBetterMatch(weight, subFamily);

          String italicSubFamily = null;
          if (baseSet == italicsByWeight) {
            italicSubFamily = subFamily;
          }
          else {
            Collection<String> italicSubFamilyCandidates = italicsByWeight.get(weight);
            for (String italicCandidate : italicSubFamilyCandidates) {
              if (italicSubFamily == null
                  // try to match by name, assuming italic variant is named by adding a suffix to the base variant
                  || italicCandidate.startsWith(subFamily)) {
                italicSubFamily = italicCandidate;
              }
            }
          }
          italics.put(subFamily, italicSubFamily == null ? members.get(subFamily).deriveFont(SYNTHETIC_ITALICS_TRANSFORM)
                                                         : members.get(italicSubFamily));

          candidates.forEach((original, candidate) -> candidate.updateIfBetterMatch(weight, subFamily));
          candidates.put(subFamily, new Candidate(subFamily, weight + PREFERRED_BOLD_WEIGHT_DIFF));
        });
        recommendedSubFamily = preferred.bestSubFamily;
        candidates.forEach((original, boldCandidate) -> recommendedBoldSubFamilies.put(original, boldCandidate.bestSubFamily));

        candidates.clear();
        baseSet.forEachDescending((weight, subFamily) -> {
          candidates.forEach((original, candidate) -> candidate.updateIfBetterMatch(weight, subFamily));
          candidates.put(subFamily, new Candidate(subFamily, weight - PREFERRED_BOLD_WEIGHT_DIFF));
        });
        candidates.forEach((original, plainCandidate) -> recommendedPlainSubFamilies.put(original, plainCandidate.bestSubFamily));
      }
    }

    private static boolean isItalic(String subFamily) {
      String name = subFamily.toLowerCase(Locale.ENGLISH);
      return name.contains("italic") || name.contains("oblique") || name.contains("inclined");
    }

    private static int getWeight(Font font) {
      assert GET_FONT_2D_METHOD != null;
      assert GET_WEIGHT_METHOD != null;
      try {
        Font2D font2d = (Font2D)GET_FONT_2D_METHOD.invoke(font);
        return (Integer)GET_WEIGHT_METHOD.invoke(font2d);
      }
      catch (Throwable t) {
        LOG.error(t);
      }
      return Font2D.FWEIGHT_NORMAL;
    }

    private boolean isMonospaced() {
      Font font = members.entrySet().iterator().next().getValue();
      if (!font.canDisplay(' ') || !font.canDisplay('M')) {
        return false;
      }
      FontMetrics metrics = FontInfo.getFontMetrics(font.deriveFont((float)EditorFontsConstants.getDefaultEditorFontSize()),
                                                    FontInfo.DEFAULT_CONTEXT);
      return metrics.charWidth(' ') == metrics.charWidth('M');
    }

    private List<String> getBaseSubFamilies() {
      initIfNeeded();
      return new ArrayList<>(italics.keySet());
    }

    private String getRecommendedSubFamily() {
      initIfNeeded();
      return recommendedSubFamily;
    }

    private String getRecommendedBoldSubFamily(String mainSubFamily) {
      initIfNeeded();
      String result = recommendedBoldSubFamilies.get(mainSubFamily);
      return result == null ? recommendedBoldSubFamilies.get(recommendedSubFamily) : result;
    }

    private Font getFont(String regularSubFamily, String boldSubFamily, int style) {
      initIfNeeded();
      if (!italics.containsKey(regularSubFamily)) {
        regularSubFamily = null;
      }
      if (!italics.containsKey(boldSubFamily)) {
        boldSubFamily = null;
      }
      if (regularSubFamily == null) {
        regularSubFamily = boldSubFamily == null ? recommendedSubFamily : recommendedPlainSubFamilies.get(boldSubFamily);
      }
      if (boldSubFamily == null) {
        boldSubFamily = recommendedBoldSubFamilies.get(regularSubFamily);
      }
      String target = (style & Font.BOLD) == 0 ? regularSubFamily : boldSubFamily;
      return (style & Font.ITALIC) == 0 ? members.get(target) : italics.get(target);
    }
  }

  private static class OurWeightMap extends MultiMap<Integer, String> {
    private OurWeightMap() {
      super(new TreeMap<>());
    }

    private void forEach(BiConsumer<? super Integer, ? super String> action) {
      myMap.forEach((weight, list) -> list.forEach(subFamily -> action.accept(weight, subFamily)));
    }

    private void forEachDescending(BiConsumer<? super Integer, ? super String> action) {
      ((TreeMap<Integer, Collection<String>>)myMap).descendingMap().forEach((weight, list) -> {
        list.forEach(subFamily -> action.accept(weight, subFamily));
      });
    }
  }
}
