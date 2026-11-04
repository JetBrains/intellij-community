// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.MethodHandleUtil;
import com.intellij.util.ui.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.font.Font2D;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;

final class FontFamilyServiceImpl extends FontFamilyService {
  private static final Logger LOG = Logger.getInstance(FontFamilyServiceImpl.class);

  private static final MethodHandle GET_FONT_2D_METHOD = getFont2DGetter();
  private static final MethodHandle GET_TYPO_FAMILY_METHOD = getFont2dMethod("getTypographicFamilyName", String.class);
  private static final MethodHandle GET_TYPO_SUBFAMILY_METHOD = getFont2dMethod("getTypographicSubfamilyName", String.class);
  private static final MethodHandle GET_WEIGHT_METHOD = getFont2dMethod("getWeight", Integer.TYPE);

  /**
   * Whether the runtime gives the typographic names and the weights of the fonts.
   */
  static final boolean RUNTIME_API_AVAILABLE = GET_FONT_2D_METHOD != null &&
                                               GET_TYPO_FAMILY_METHOD != null &&
                                               GET_TYPO_SUBFAMILY_METHOD != null &&
                                               GET_WEIGHT_METHOD != null;

  // Fira Code requires specific migration due to naming workaround in JBR used earlier
  private static final Map<String, String[]> FIRA_CODE_MIGRATION_MAP = Map.of(
    "Fira Code Light", new String[]{"Fira Code", "Light", "Light"},
    "Fira Code Medium", new String[]{"Fira Code", "Medium", "Medium"},
    "Fira Code Retina", new String[]{"Fira Code", "Retina", "Retina"}
  );

  /**
   * The typographic families by name. The map is empty when the service works in the fallback mode of {@link FontFamilyService}.
   */
  private final Map<String, FontFamily> myFamilies;

  /**
   * The names of {@link #myFamilies} in alphabetical order.
   */
  private final List<String> myFamilyNames;

  private FontFamilyServiceImpl() {
    myFamilies = scanInstalledFonts();
    myFamilyNames = myFamilies.keySet().stream().sorted().toList();
  }

  /**
   * Groups the fonts by their typographic family names.
   * Skips a font that the runtime replaces with the Dialog font.
   * The runtime must give the typographic names, see {@link #RUNTIME_API_AVAILABLE}.
   *
   * @return an immutable map of the families by name
   */
  static Map<String, FontFamily> loadFamilies(Font[] fonts) throws Throwable {
    Map<String, Map<String, Font>> membersByFamily = new HashMap<>();
    for (Font font : fonts) {
      Font2D font2D = getFont2D(font);
      String fontName = font.getName();
      String font2DName = font2D.getFontName(null);
      if (isUnsupportedFont(fontName, font2DName)) {
        // skip fonts that are declared as available, but cannot be used due to some reason,
        // with JDK substituting them with Dialog logical font (on Windows)
        if (FontFamily.VERBOSE_LOGGING) {
          LOG.info("Skipping '" + fontName + "' as it's mapped to '" + font2DName + "' by the runtime");
        }
        continue;
      }
      TypographicName name = getTypographicName(font2D);
      if (FontFamily.VERBOSE_LOGGING) {
        LOG.info("Adding " + fontName + " as " + name.subfamily() + " variant to " + name.family() + " family");
      }
      membersByFamily.computeIfAbsent(
        name.family(),
        _ -> new LinkedHashMap<>()
      ).put(name.subfamily(), font);
    }
    Map<String, FontFamily> families = new HashMap<>();
    for (var entry : membersByFamily.entrySet()) {
      String family = entry.getKey();
      Map<String, Font> members = entry.getValue();
      FontFamily newFontFamily = new FontFamily(family, members, FontFamilyServiceImpl::getWeight);
      families.put(family, newFontFamily);
    }
    return Map.copyOf(families);
  }

  @Override
  protected boolean isSupportedImpl() {
    return !myFamilies.isEmpty();
  }

  @Override
  protected @NotNull List<String> getAvailableFamiliesImpl() {
    return myFamilies.isEmpty() ? super.getAvailableFamiliesImpl() : myFamilyNames;
  }

  @Override
  protected boolean isAvailableImpl(String family) {
    return myFamilies.isEmpty() ? super.isAvailableImpl(family) : myFamilies.containsKey(family);
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
    return fontFamily == null
           ? super.getRecommendedBoldSubFamilyImpl(family, mainSubFamily)
           : fontFamily.getRecommendedBoldSubFamily(mainSubFamily);
  }

  @Override
  protected @NotNull Font getFontImpl(
    @NotNull String family,
    @Nullable String regularSubFamily,
    @Nullable String boldSubFamily,
    @JdkConstants.FontStyle int style
  ) {
    FontFamily fontFamily = myFamilies.get(family);
    return fontFamily == null
           ? super.getFontImpl(family, regularSubFamily, boldSubFamily, style)
           : fontFamily.getFont(regularSubFamily, boldSubFamily, style);
  }

  @Override
  protected @Nullable FontFamilyDescriptor getDescriptorByFontImpl(@NotNull Font font) {
    if (myFamilies.isEmpty()) {
      return null;
    }
    try {
      Font2D font2D = getFont2D(font);
      if (isUnsupportedFont(font.getName(), font2D.getFontName(null))) {
        return null;
      }
      TypographicName name = getTypographicName(font2D);
      FontFamilyDescriptor descriptor = new FontFamilyDescriptor(name.family(), name.subfamily());
      if (getFontByDescriptorImpl(descriptor) == null) {
        return null;
      }
      return descriptor;
    } catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.warn(e);
      return null;
    }
  }

  @Override
  protected @Nullable Font getFontByDescriptorImpl(@NotNull FontFamilyDescriptor descriptor) {
    FontFamily fontFamily = myFamilies.get(descriptor.getFamily());
    return fontFamily == null ? null : fontFamily.getMember(descriptor.getSubfamily());
  }

  @Override
  protected String @NotNull [] migrateFontSettingImpl(@NotNull String family) {
    if (myFamilies.isEmpty()) {
      return super.migrateFontSettingImpl(family);
    }
    String[] firaCodeSetting = FIRA_CODE_MIGRATION_MAP.get(family);
    if (firaCodeSetting != null) {
      // check for new enough JBR
      if (myFamilies.containsKey(family)) {
        return super.migrateFontSettingImpl(family);
      }
      return firaCodeSetting.clone();
    }
    try {
      String[] setting = migrateToTypographicSetting(family);
      if (setting != null) {
        return setting;
      }
    } catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.error(e);
    }
    return super.migrateFontSettingImpl(family);
  }

  /**
   * Returns the typographic family, the regular subfamily and the bold subfamily for an old font setting,
   * or {@code null} when the setting cannot migrate. The bold subfamily is {@code null} when it is the regular one.
   */
  private String @Nullable [] migrateToTypographicSetting(String family) throws Throwable {
    Font regularFont = new Font(family, Font.PLAIN, 1);
    TypographicName regular = getTypographicName(getFont2D(regularFont));
    Font boldFont = new Font(family, Font.BOLD, 1);
    TypographicName bold = getTypographicName(getFont2D(boldFont));
    if (!family.equals(regularFont.getFamily()) || !family.equals(boldFont.getFamily())) {
      return cannotMigrate(family, "unexpected resolved families - " + regularFont.getFamily() + ", " + boldFont.getFamily());
    }
    if (!Objects.equals(regular.family(), bold.family())) {
      return cannotMigrate(
        family,
        "normal and bold variations resolve to different typographic families - " + regular.family() + ", " + bold.family()
      );
    }
    FontFamily fontFamily = myFamilies.get(regular.family());
    if (fontFamily == null) {
      return cannotMigrate(family, "typographic font family not found - " + regular.family());
    }
    if (!fontFamily.hasBaseSubFamily(regular.subfamily())) {
      return cannotMigrate(family, "subfamily " + regular.subfamily() + " not found in typographic font family " + regular.family());
    }
    if (!fontFamily.hasBaseSubFamily(bold.subfamily())) {
      return cannotMigrate(family, "subfamily " + bold.subfamily() + " not found in typographic font family " + regular.family());
    }
    String boldSubfamily = Objects.equals(regular.subfamily(), bold.subfamily()) ? null : bold.subfamily();
    return new String[]{regular.family(), regular.subfamily(), boldSubfamily};
  }

  private static String @Nullable [] cannotMigrate(String family, String reason) {
    LOG.info("Cannot migrate " + family + ": " + reason);
    return null;
  }

  /**
   * Reads the typographic families of the installed fonts.
   * Returns an empty map for the fallback mode: in a unit test, in a headless environment, without the runtime API, or after an error.
   */
  private static Map<String, FontFamily> scanInstalledFonts() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      return Map.of();
    }
    if (!RUNTIME_API_AVAILABLE) {
      LOG.warn("Couldn't access required runtime API, will fall back to basic logic of font selection");
      return Map.of();
    }
    try {
      return loadFamilies(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());
    } catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.error(e);
      return Map.of(); // fallback to old behavior in case of any errors
    }
  }

  private static boolean isUnsupportedFont(@NotNull String fontName, @NotNull String font2DName) {
    return font2DName.startsWith(Font.DIALOG) && !fontName.startsWith(Font.DIALOG);
  }

  private static Font2D getFont2D(Font font) throws Throwable {
    assert GET_FONT_2D_METHOD != null;
    return (Font2D)GET_FONT_2D_METHOD.invoke(font);
  }

  private static @NotNull TypographicName getTypographicName(Font2D font2D) throws Throwable {
    assert GET_TYPO_FAMILY_METHOD != null;
    assert GET_TYPO_SUBFAMILY_METHOD != null;
    String family = (String)GET_TYPO_FAMILY_METHOD.invoke(font2D);
    String subfamily = (String)GET_TYPO_SUBFAMILY_METHOD.invoke(font2D);
    return new TypographicName(family, subfamily);
  }

  private static int getWeight(Font font) {
    assert GET_WEIGHT_METHOD != null;
    try {
      return (Integer)GET_WEIGHT_METHOD.invoke(getFont2D(font));
    } catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.error(e);
      return Font2D.FWEIGHT_NORMAL;
    }
  }

  private static @Nullable MethodHandle getFont2DGetter() {
    try {
      return MethodHandleUtil.getPrivateMethod(Font.class, "getFont2D", MethodType.methodType(Font2D.class));
    } catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.warn(e);
      return null;
    }
  }

  private static @Nullable MethodHandle getFont2dMethod(@NotNull String methodName, @NotNull Class<?> type) {
    try {
      return MethodHandleUtil.getPublicMethod(Font2D.class, methodName, MethodType.methodType(type));
    } catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.debug(e);
      return null;
    }
  }

  /**
   * The typographic family and subfamily names of a font.
   */
  private record TypographicName(String family, String subfamily) {
  }
}
