// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.ToIntFunction;

/**
 * A typographic font family of the runtime, with its fonts by subfamily name.
 * It chooses the subfamilies of the regular, bold and italic text by the weights of its fonts.
 * <p>
 * The instances are immutable and safe to use on any thread.
 * The family makes its choice on the first use, because the choice reads the weight of each font.
 */
final class FontFamily {
  /**
   * Turns on the info log of the font scan of {@link FontFamilyServiceImpl} and of the subfamily choice.
   * The scan can run before the debug log settings load, so the flag is a system property.
   */
  static final boolean VERBOSE_LOGGING = Boolean.getBoolean("font.family.service.verbose");

  private static final Logger LOG = Logger.getInstance(FontFamily.class);
  private static final double SYNTHETIC_ITALIC_SHEAR = -0.2;
  private static final int PREFERRED_MAIN_WEIGHT = 400;
  private static final int PREFERRED_BOLD_WEIGHT_DIFF = 300;
  private static final List<String> ITALIC_NAMES = List.of("italic", "oblique", "inclined");

  private final String name;
  private final SequencedMap<String, Font> members;
  private final ToIntFunction<? super Font> weightReader;
  private final Object recommendedSubFamiliesLock = new Object();
  private volatile @Nullable RecommendedSubFamilies recommendedSubFamilies;
  private final Object monospacedLock = new Object();
  private volatile @Nullable Boolean monospaced;

  /**
   * @param members      the fonts of the family by subfamily name, in the order of the fonts of the runtime.
   *                     The family keeps a copy. A family has at least one font.
   * @param weightReader gives the weight of a font, for example 400 for a regular font and 700 for a bold font
   * @throws IllegalArgumentException when the family has no fonts
   */
  FontFamily(
    @NotNull String name,
    Map<String, Font> members,
    ToIntFunction<? super Font> weightReader
  ) {
    requireFonts(name, members);
    this.name = name;
    this.members = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(members));
    this.weightReader = weightReader;
  }

  /**
   * Returns the subfamilies that are not italic, in the order of their weights, as an immutable list.
   * When all fonts of the family are italic, returns the italic subfamilies.
   */
  List<String> getBaseSubFamilies() {
    return getRecommendedSubFamilies().baseSubFamilies();
  }

  String getRecommendedSubFamily() {
    return getRecommendedSubFamilies().regular();
  }

  /**
   * Returns the bold subfamily of the main subfamily.
   * When the main subfamily is not a base subfamily, returns the bold subfamily of the recommended subfamily.
   */
  String getRecommendedBoldSubFamily(String mainSubFamily) {
    return getRecommendedSubFamilies().baseOrRegular(mainSubFamily).bold();
  }

  /**
   * Returns the font of the style for the regular and the bold subfamilies that a user chose.
   * A subfamily that is {@code null} or not a base subfamily gets a recommended one.
   */
  Font getFont(
    @Nullable String regularSubFamily,
    @Nullable String boldSubFamily,
    @FontStyle int style
  ) {
    RecommendedSubFamilies recommended = getRecommendedSubFamilies();
    Map<String, BaseSubFamily> bases = recommended.bases();
    BaseSubFamily boldBase = boldSubFamily == null ? null : bases.get(boldSubFamily);
    String regular;
    if (regularSubFamily != null && bases.containsKey(regularSubFamily)) {
      regular = regularSubFamily;
    } else if (boldBase == null) {
      regular = recommended.regular();
    } else {
      regular = boldBase.plain();
    }
    String bold = boldBase == null ? bases.get(regular).bold() : boldSubFamily;
    String target = (style & Font.BOLD) == 0 ? regular : bold;
    if ((style & Font.ITALIC) == 0) {
      return members.get(target);
    }
    return bases.get(target).italicFont();
  }

  /**
   * Returns the font of the subfamily, or {@code null} when the family has no such subfamily.
   * An italic subfamily is a subfamily too.
   */
  @Nullable Font getMember(String subFamily) {
    return members.get(subFamily);
  }

  /**
   * Tells whether the subfamily is a base subfamily, see {@link #getBaseSubFamilies()}.
   */
  boolean hasBaseSubFamily(String subFamily) {
    return subFamily != null && getRecommendedSubFamilies().bases().containsKey(subFamily);
  }

  boolean isMonospaced() {
    Boolean result = monospaced;
    if (result == null) {
      synchronized (monospacedLock) {
        result = monospaced;
        if (result == null) {
          result = measureMonospaced();
          monospaced = result;
        }
      }
    }
    return result;
  }

  private boolean measureMonospaced() {
    Font font = members.firstEntry().getValue();
    if (!font.canDisplay(' ') || !font.canDisplay('M')) {
      return false;
    }
    FontMetrics metrics = FontInfo.getFontMetrics(
      font.deriveFont((float)EditorFontsConstants.getDefaultEditorFontSize()),
      FontInfo.DEFAULT_CONTEXT
    );
    return metrics.charWidth(' ') == metrics.charWidth('M');
  }

  private RecommendedSubFamilies getRecommendedSubFamilies() {
    RecommendedSubFamilies result = recommendedSubFamilies;
    if (result == null) {
      synchronized (recommendedSubFamiliesLock) {
        result = recommendedSubFamilies;
        if (result == null) {
          result = chooseSubFamilies();
          recommendedSubFamilies = result;
        }
      }
    }
    return result;
  }

  /**
   * Chooses the subfamilies by the weights of the fonts.
   * The regular subfamily is the base subfamily nearest to the weight {@value #PREFERRED_MAIN_WEIGHT}.
   * The bold subfamily of a base subfamily is the nearest one to its weight plus {@value #PREFERRED_BOLD_WEIGHT_DIFF},
   * among the base subfamilies after it in the ascending order of the weights.
   * The plain subfamily is the nearest one to its weight minus {@value #PREFERRED_BOLD_WEIGHT_DIFF},
   * among the base subfamilies after it in the descending order of the weights.
   * Both orders keep the order of the fonts of the runtime for equal weights.
   */
  private @NotNull RecommendedSubFamilies chooseSubFamilies() {
    List<WeightedSubFamily> nonItalics = new ArrayList<>();
    List<WeightedSubFamily> italics = new ArrayList<>();
    for (Map.Entry<String, Font> member : members.entrySet()) {
      String subFamily = member.getKey();
      int weight = weightReader.applyAsInt(member.getValue());
      boolean isItalic = isItalic(subFamily);
      if (VERBOSE_LOGGING) {
        LOG.info(name + "(" + subFamily + "): weight=" + weight + (isItalic ? ", italic" : ""));
      }
      (isItalic ? italics : nonItalics).add(new WeightedSubFamily(subFamily, weight));
    }
    boolean onlyItalics = nonItalics.isEmpty();
    List<WeightedSubFamily> ascending = new ArrayList<>(onlyItalics ? italics : nonItalics);
    ascending.sort(Comparator.comparingInt(WeightedSubFamily::weight));
    List<WeightedSubFamily> descending = new ArrayList<>(ascending);
    descending.sort(Comparator.comparingInt(WeightedSubFamily::weight).reversed());
    Map<String, String> plainSubFamilies = new HashMap<>();
    for (int i = 0; i < descending.size(); i++) {
      WeightedSubFamily base = descending.get(i);
      String nearestMatch = nearest(descending, i + 1, base.weight() - PREFERRED_BOLD_WEIGHT_DIFF, base.name());
      plainSubFamilies.put(base.name(), nearestMatch);
    }
    SequencedMap<String, BaseSubFamily> bases = new LinkedHashMap<>();
    for (int i = 0; i < ascending.size(); i++) {
      WeightedSubFamily base = ascending.get(i);
      String bold = nearest(ascending, i + 1, base.weight() + PREFERRED_BOLD_WEIGHT_DIFF, base.name());
      Font italicFont = onlyItalics ? members.get(base.name()) : chooseItalicFont(base, italics);
      bases.put(base.name(), new BaseSubFamily(bold, plainSubFamilies.get(base.name()), italicFont));
    }
    String regular = nearest(ascending, 0, PREFERRED_MAIN_WEIGHT, ascending.getFirst().name());
    return new RecommendedSubFamilies(regular, List.copyOf(bases.keySet()), Collections.unmodifiableSequencedMap(bases));
  }

  /**
   * Returns the italic font of the base subfamily: an italic font of the same weight, or else a synthetic italic of the base font.
   * Of several italic fonts of the weight, the last one that matches the name of the base subfamily wins, or else the first one.
   */
  private Font chooseItalicFont(WeightedSubFamily base, @NotNull List<WeightedSubFamily> italics) {
    String italicSubFamily = null;
    for (WeightedSubFamily italic : italics) {
      if (italic.weight() == base.weight() &&
          (italicSubFamily == null || isMatchingItalic(base.name(), italic.name()))) {
        italicSubFamily = italic.name();
      }
    }
    if (italicSubFamily == null) {
      return members.get(base.name())
        .deriveFont(AffineTransform.getShearInstance(SYNTHETIC_ITALIC_SHEAR, 0));
    }
    return members.get(italicSubFamily);
  }

  /**
   * Returns the subfamily nearest to the weight, among the subfamilies of the list from the index on.
   * Of two subfamilies at the same distance, the first one wins.
   * Returns the default subfamily when the list has no subfamily from the index on.
   */
  private static String nearest(
    @NotNull List<WeightedSubFamily> subFamilies,
    int fromIndex,
    int weight,
    String defaultSubFamily
  ) {
    String result = defaultSubFamily;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = fromIndex; i < subFamilies.size(); i++) {
      WeightedSubFamily candidate = subFamilies.get(i);
      int distance = Math.abs(weight - candidate.weight());
      if (distance < bestDistance) {
        bestDistance = distance;
        result = candidate.name();
      }
    }
    return result;
  }

  private static boolean isItalic(@NotNull String subFamily) {
    String name = subFamily.toLowerCase(Locale.ENGLISH);
    return ContainerUtil.exists(ITALIC_NAMES, name::contains);
  }

  private static boolean isMatchingItalic(
    @NotNull String mainSubFamily,
    @NotNull String italicSubFamily
  ) {
    String main = mainSubFamily.toLowerCase(Locale.ENGLISH);
    String candidate = italicSubFamily.toLowerCase(Locale.ENGLISH);
    // assuming italic variant is named by adding a suffix to the base variant
    for (String suffix : ITALIC_NAMES) {
      if (candidate.endsWith(suffix)) {
        candidate = candidate.substring(0, candidate.length() - suffix.length()).trim();
        break;
      }
    }
    return candidate.equals(main) ||
           // 'Regular' is a special case,
           // corresponding italic variant is usually called 'Italic', not 'Regular Italic'
           "regular".equals(main) && candidate.isEmpty();
  }

  private static void requireFonts(String name, @NotNull Map<String, Font> members) {
    if (members.isEmpty()) {
      throw new IllegalArgumentException("The font family '" + name + "' has no fonts");
    }
  }

  private record WeightedSubFamily(
    String name,
    int weight
  ) {
  }

  /**
   * The subfamilies and the italic font that a family chose for one base subfamily.
   *
   * @param bold       the bold subfamily
   * @param plain      the plain subfamily, for a user who chose this subfamily for the bold text
   * @param italicFont the italic font
   */
  private record BaseSubFamily(
    String bold,
    String plain,
    Font italicFont
  ) {
  }

  /**
   * The subfamilies that a family chose.
   *
   * @param regular         the subfamily of the regular text
   * @param baseSubFamilies the base subfamilies in the order of their weights
   * @param bases           the choice for each base subfamily, in the same order
   */
  private record RecommendedSubFamilies(
    String regular,
    List<String> baseSubFamilies,
    Map<String, BaseSubFamily> bases
  ) {
    /**
     * Returns the choice for the subfamily.
     * When the subfamily is {@code null} or not a base subfamily, returns the choice for the regular subfamily.
     */
    BaseSubFamily baseOrRegular(@Nullable String subFamily) {
      BaseSubFamily base = subFamily == null ? null : bases.get(subFamily);
      return base == null ? bases.get(regular) : base;
    }
  }
}
