// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Eugene.Kudelevsky
 */
public class StructuralSearchUtil {
  private static final String REG_EXP_META_CHARS = ".$|()[]{}^?*+\\";
  private static final Key<StructuralSearchProfile> STRUCTURAL_SEARCH_PROFILE_KEY = new Key<>("Structural Search Profile");
  private static final Pattern ACCENTS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
  private static LanguageFileType ourDefaultFileType = null;

  public static boolean ourUseUniversalMatchingAlgorithm = false;
  private static StructuralSearchProfile[] ourNewStyleProfiles;
  private static List<Configuration> ourPredefinedConfigurations = null;

  private StructuralSearchUtil() {}

  @Nullable
  public static StructuralSearchProfile getProfileByPsiElement(@NotNull PsiElement element) {
    return getProfileByLanguage(element.getLanguage());
  }

  @Contract("null -> false")
  public static boolean isIdentifier(PsiElement element) {
    if (element == null) return false;
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    return profile != null && profile.isIdentifier(element);
  }

  public static PsiElement getParentIfIdentifier(PsiElement element) {
    return !isIdentifier(element) ? element : element.getParent();
  }

  @Contract("!null -> !null")
  public static PsiElement getPresentableElement(PsiElement element) {
    if (element == null) return null;
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    return profile == null ? element : profile.getPresentableElement(element);
  }

  private static StructuralSearchProfile[] getNewStyleProfiles() {
    if (ourNewStyleProfiles == null) {
      final List<StructuralSearchProfile> list = new ArrayList<>();

      for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensions()) {
        if (profile instanceof StructuralSearchProfileBase) {
          list.add(profile);
        }
      }
      list.add(new XmlStructuralSearchProfile());
      ourNewStyleProfiles = list.toArray(new StructuralSearchProfile[0]);
    }
    return ourNewStyleProfiles;
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
        ourDefaultFileType = StdFileTypes.XML;
      }
    }
    return ourDefaultFileType;
  }

  @TestOnly
  public static void clearProfileCache(@NotNull Language language) {
    language.putUserData(STRUCTURAL_SEARCH_PROFILE_KEY, null);
  }

  @Nullable
  public static StructuralSearchProfile getProfileByLanguage(@NotNull Language language) {
    final StructuralSearchProfile cachedProfile = language.getUserData(STRUCTURAL_SEARCH_PROFILE_KEY);
    if (cachedProfile != null) return cachedProfile;
    for (StructuralSearchProfile profile : getProfiles()) {
      if (profile.isMyLanguage(language)) {
        language.putUserData(STRUCTURAL_SEARCH_PROFILE_KEY, profile);
        return profile;
      }
    }
    return null;
  }

  public static boolean isTypedVariable(@NotNull String name) {
    return name.length() > 1 && name.charAt(0) == '$' && name.charAt(name.length() - 1) == '$';
  }

  @Nullable
  public static StructuralSearchProfile getProfileByFileType(LanguageFileType fileType) {
    return getProfileByLanguage(fileType.getLanguage());
  }

  @NotNull
  public static LanguageFileType[] getSuitableFileTypes() {
    final FileType[] types = FileTypeManager.getInstance().getRegisteredFileTypes();
    final Set<LanguageFileType> fileTypes = StreamEx.of(types).select(LanguageFileType.class).collect(Collectors.toSet());
    for (Language language : Language.getRegisteredLanguages()) {
      final LanguageFileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        fileTypes.add(fileType);
      }
    }

    return fileTypes.toArray(new LanguageFileType[0]);
  }

  public static boolean containsRegExpMetaChar(String s) {
    return s.chars().anyMatch(StructuralSearchUtil::isRegExpMetaChar);
  }

  public static boolean isRegExpMetaChar(int ch) {
    return REG_EXP_META_CHARS.indexOf(ch) >= 0;
  }

  public static String shieldRegExpMetaChars(String word) {
    return shieldRegExpMetaChars(word, new StringBuilder(word.length())).toString();
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
      Collections.sort(result);
      ourPredefinedConfigurations = Collections.unmodifiableList(result);
    }
    return ourPredefinedConfigurations;
  }

  public static boolean isDocCommentOwner(PsiElement match) {
    final StructuralSearchProfile profile = getProfileByPsiElement(match);
    return profile != null && profile.isDocCommentOwner(match);
  }

  public static String getMeaningfulText(PsiElement matchedNode) {
    final StructuralSearchProfile profile = getProfileByPsiElement(matchedNode);
    return profile != null ? profile.getMeaningfulText(matchedNode) : matchedNode.getText();
  }

  public static String getAlternativeText(PsiElement matchedNode, String previousText) {
    final StructuralSearchProfile profile = getProfileByPsiElement(matchedNode);
    return profile != null ? profile.getAlternativeText(matchedNode, previousText) : null;
  }

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

  public static String stripAccents(@NotNull String input) {
    return ACCENTS.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
  }

  public static String normalize(@NotNull String text) {
    return stripAccents(normalizeWhiteSpace(text));
  }

  public static PatternContext findPatternContextByID(String id, Language language) {
    return findPatternContextByID(id, getProfileByLanguage(language));
  }

  public static PatternContext findPatternContextByID(String id, StructuralSearchProfile profile) {
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
