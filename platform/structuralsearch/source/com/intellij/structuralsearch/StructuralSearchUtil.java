// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Eugene.Kudelevsky
 */
public final class StructuralSearchUtil {
  private static final String REG_EXP_META_CHARS = ".$|()[]{}^?*+\\";
  private static final Pattern ACCENTS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
  private static final Comparator<? super Configuration> CONFIGURATION_COMPARATOR =
    Comparator.comparing(Configuration::getCategory, NaturalComparator.INSTANCE)
      .thenComparing(Configuration::getName, NaturalComparator.INSTANCE);
  private static LanguageFileType ourDefaultFileType;

  private static boolean ourUseUniversalMatchingAlgorithm;
  private static Map<String, LanguageFileType> ourNames2FileTypes;
  private static final Map<String, StructuralSearchProfile> cache = new HashMap<>();

  private static List<Configuration> ourPredefinedConfigurations;
  static {
    StructuralSearchProfile.EP_NAME.addChangeListener(() -> {
      ourPredefinedConfigurations = null;
      ourDefaultFileType = null;
      ourNames2FileTypes = null;
      cache.clear();
    }, null);
  }

  private StructuralSearchUtil() {}

  public static void setUseUniversalMatchingAlgorithm(boolean useUniversalMatchingAlgorithm) {
    ourUseUniversalMatchingAlgorithm = useUniversalMatchingAlgorithm;
    cache.clear();
  }

  @Nullable
  public static StructuralSearchProfile getProfileByPsiElement(@NotNull PsiElement element) {
    return getProfileByLanguage(element.getLanguage());
  }

  @Nullable
  public static StructuralSearchProfile getProfileByFileType(@Nullable LanguageFileType fileType) {
    return (fileType == null) ? null : getProfileByLanguage(fileType.getLanguage());
  }

  @Nullable
  public static StructuralSearchProfile getProfileByLanguage(@NotNull Language language) {
    final String id = language.getID();
    if (cache.containsKey(id)) {
      return cache.get(id);
    }
    for (StructuralSearchProfile profile : getProfiles()) {
      if (profile.isMyLanguage(language)) {
        cache.put(id, profile);
        return profile;
      }
    }
    cache.put(id, null);
    return null;
  }

  @Contract("null -> false")
  public static boolean isIdentifier(PsiElement element) {
    if (element == null) return false;
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    return profile != null && profile.isIdentifier(element);
  }

  public static PsiElement getParentIfIdentifier(PsiElement element) {
    return isIdentifier(element) ? element.getParent() : element;
  }

  @Contract("!null -> !null")
  public static PsiElement getPresentableElement(PsiElement element) {
    if (element == null) return null;
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    return profile == null ? element : profile.getPresentableElement(element);
  }

  private static StructuralSearchProfile[] getNewStyleProfiles() {
    final List<StructuralSearchProfile> list = new SmartList<>();
    for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
      if (profile instanceof StructuralSearchProfileBase) {
        list.add(profile);
      }
    }
    list.add(new XmlStructuralSearchProfile());
    return list.toArray(new StructuralSearchProfile[0]);
  }

  private static StructuralSearchProfile[] getProfiles() {
    return ourUseUniversalMatchingAlgorithm
           ? getNewStyleProfiles()
           : StructuralSearchProfile.EP_NAME.getExtensions();
  }

  @NotNull
  public static LanguageFileType getDefaultFileType() {
    if (ourDefaultFileType == null) {
      for (StructuralSearchProfile profile : getProfiles()) {
        ourDefaultFileType = profile.getDefaultFileType(ourDefaultFileType);
      }
      if (ourDefaultFileType == null) {
        ourDefaultFileType = XmlFileType.INSTANCE;
      }
    }
    return ourDefaultFileType;
  }

  public static boolean isTypedVariable(@NotNull String name) {
    return name.length() > 1 && name.charAt(0) == '$' && name.charAt(name.length() - 1) == '$';
  }

  public static Collection<LanguageFileType> getSuitableFileTypes() {
    return Collections.unmodifiableCollection(getNames2FileTypes().values());
  }

  private static @NotNull Map<String, LanguageFileType> getNames2FileTypes() {
    final Map<String, LanguageFileType> names2FileTypes = ourNames2FileTypes;
    if (names2FileTypes != null) {
      return names2FileTypes;
    }

    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    final Map<String, LanguageFileType> cache = Arrays.stream(fileTypes)
      .filter(fileType -> fileType instanceof LanguageFileType)
      .collect(Collectors.toMap(FileType::getName, fileType -> (LanguageFileType)fileType));
    for (Language language : Language.getRegisteredLanguages()) {
      final LanguageFileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        cache.put(fileType.getName(), fileType);
      }
    }
    return ourNames2FileTypes = cache;
  }

  public static LanguageFileType getSuitableFileTypeByName(String name) {
    return getNames2FileTypes().get(name);
  }

  public static boolean containsRegExpMetaChar(String s) {
    return s.chars().anyMatch(StructuralSearchUtil::isRegExpMetaChar);
  }

  public static boolean isRegExpMetaChar(int ch) {
    return REG_EXP_META_CHARS.indexOf(ch) >= 0;
  }

  @NotNull
  public static String shieldRegExpMetaChars(@NotNull String word) {
    return shieldRegExpMetaChars(word, new StringBuilder(word.length())).toString();
  }

  public static String makeExtremeSpacesOptional(String word) {
    if (word.trim().isEmpty()) return word;

    String result = word;
    if (word.startsWith(" ")) result = "(?:\\s|\\b)" + result.substring(1);
    if (word.endsWith(" ")) result = result.substring(0, result.length() - 1) + "(?:\\s|\\b)";
    return result;
  }

  @NotNull
  public static StringBuilder shieldRegExpMetaChars(String word, StringBuilder out) {
    for (int i = 0, length = word.length(); i < length; ++i) {
      if (isRegExpMetaChar(word.charAt(i))) {
        out.append("\\");
      }
      out.append(word.charAt(i));
    }

    return out;
  }

  public static Pattern[] createPatterns(String[] prefixes) {
    final Pattern[] patterns = new Pattern[prefixes.length];

    for (int i = 0; i < prefixes.length; i++) {
      final String s = shieldRegExpMetaChars(prefixes[i]);
      patterns[i] = Pattern.compile("\\b(" + s + "\\w+)\\b");
    }
    return patterns;
  }

  public static List<Configuration> getPredefinedTemplates() {
    if (ourPredefinedConfigurations == null) {
      final List<Configuration> result = new ArrayList<>();
      for (StructuralSearchProfile profile : getProfiles()) {
        Collections.addAll(result, profile.getPredefinedTemplates());
      }
      Collections.sort(result, CONFIGURATION_COMPARATOR);
      ourPredefinedConfigurations = Collections.unmodifiableList(result);
    }
    return ourPredefinedConfigurations;
  }

  public static boolean isDocCommentOwner(@NotNull PsiElement match) {
    final StructuralSearchProfile profile = getProfileByPsiElement(match);
    return profile != null && profile.isDocCommentOwner(match);
  }

  public static String getMeaningfulText(PsiElement matchedNode) {
    final StructuralSearchProfile profile = getProfileByPsiElement(matchedNode);
    return profile != null ? profile.getMeaningfulText(matchedNode) : matchedNode.getText();
  }

  public static String getAlternativeText(@NotNull PsiElement matchedNode, @NotNull String previousText) {
    final StructuralSearchProfile profile = getProfileByPsiElement(matchedNode);
    return profile != null ? profile.getAlternativeText(matchedNode, previousText) : null;
  }

  @NotNull
  public static String normalizeWhiteSpace(@NotNull String text) {
    text = text.trim();
    final StringBuilder result = new StringBuilder();
    boolean white = false;
    for (int i = 0, length = text.length(); i < length; i++) {
      final char c = text.charAt(i);
      if (StringUtil.isWhiteSpace(c)) {
        if (!white) {
          result.append(' ');
          white = true;
        }
      }
      else {
        white = false;
        result.append(c);
      }
    }
    return result.toString();
  }

  @NotNull
  public static String stripAccents(@NotNull String input) {
    return ACCENTS.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
  }

  @NotNull
  public static String normalize(@NotNull String text) {
    return stripAccents(normalizeWhiteSpace(text));
  }

  public static PatternContext findPatternContextByID(@Nullable String id, @NotNull Language language) {
    return findPatternContextByID(id, getProfileByLanguage(language));
  }

  public static PatternContext findPatternContextByID(@Nullable String id, @Nullable StructuralSearchProfile profile) {
    if (profile == null) {
      return null;
    }
    final List<PatternContext> patternContexts = profile.getPatternContexts();
    if (patternContexts.isEmpty()) {
      return null;
    }
    if (id == null) {
      return patternContexts.get(0);
    }
    return patternContexts.stream().filter(context -> context.getId().equals(id)).findFirst().orElse(patternContexts.get(0));
  }
}
