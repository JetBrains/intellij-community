// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class StructuralSearchUtil {
  private static final String REG_EXP_META_CHARS = ".$|()[]{}^?*+\\";
  private static final Key<StructuralSearchProfile> STRUCTURAL_SEARCH_PROFILE_KEY = new Key<>("Structural Search Profile");
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

  @NotNull
  public static PsiElement getPresentableElement(@NotNull PsiElement element) {
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    if (profile == null) {
      return element;
    }
    return profile.getPresentableElement(element);
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
      ourNewStyleProfiles = list.toArray(new StructuralSearchProfile[list.size()]);
    }
    return ourNewStyleProfiles;
  }

  private static StructuralSearchProfile[] getProfiles() {
    return ourUseUniversalMatchingAlgorithm
           ? getNewStyleProfiles()
           : StructuralSearchProfile.EP_NAME.getExtensions();
  }

  public static FileType getDefaultFileType() {
    if (ourDefaultFileType == null) {
      for (StructuralSearchProfile profile : getProfiles()) {
        ourDefaultFileType = profile.getDefaultFileType(ourDefaultFileType);
      }
      if (ourDefaultFileType == null) {
        ourDefaultFileType = StdFileTypes.XML;
      }
    }
    assert ourDefaultFileType instanceof LanguageFileType : "file type not valid for structural search: " + ourDefaultFileType.getName();
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

  public static boolean isTypedVariable(@NotNull final String name) {
    return name.length() > 1 && name.charAt(0)=='$' && name.charAt(name.length()-1)=='$';
  }

  @Nullable
  public static StructuralSearchProfile getProfileByFileType(FileType fileType) {
    if (!(fileType instanceof LanguageFileType)) {
      return null;
    }
    final LanguageFileType languageFileType = (LanguageFileType)fileType;
    return getProfileByLanguage(languageFileType.getLanguage());
  }

  @NotNull
  public static FileType[] getSuitableFileTypes() {
    Set<FileType> allFileTypes = new HashSet<>();
    Collections.addAll(allFileTypes, FileTypeManager.getInstance().getRegisteredFileTypes());
    for (Language language : Language.getRegisteredLanguages()) {
      FileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        allFileTypes.add(fileType);
      }
    }

    List<FileType> result = new ArrayList<>();
    for (FileType fileType : allFileTypes) {
      if (fileType instanceof LanguageFileType) {
        result.add(fileType);
      }
    }

    return result.toArray(new FileType[result.size()]);
  }

  public static boolean containsRegExpMetaChar(String s) {
    return s.chars().anyMatch(StructuralSearchUtil::isRegExpMetaChar);
  }

  private static boolean isRegExpMetaChar(int ch) {
    return REG_EXP_META_CHARS.indexOf(ch) >= 0;
  }

  public static String shieldRegExpMetaChars(String word) {
    final StringBuilder buf = new StringBuilder(word.length());

    for (int i = 0; i < word.length(); ++i) {
      if (isRegExpMetaChar(word.charAt(i))) {
        buf.append("\\");
      }
      buf.append(word.charAt(i));
    }

    return buf.toString();
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

  public static Class getElementContextByPsi(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    final StructuralSearchProfile profile = getProfileByPsiElement(element);
    return profile == null ? element.getClass() : profile.getElementContextByPsi(element);
  }
}
