package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.*;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class StructuralSearchUtil {
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
    return profile.getPresentableElement(getParentIfIdentifier(element));
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

  @Nullable
  public static StructuralSearchProfile getProfileByLanguage(@NotNull Language language) {

    for (StructuralSearchProfile profile : getProfiles()) {
      if (profile.isMyLanguage(language)) {
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

    for (StructuralSearchProfile profile : getProfiles()) {
      if (profile.canProcess(fileType)) {
        return profile;
      }
    }

    return null;
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

  public static String shieldSpecialChars(String word) {
    final StringBuilder buf = new StringBuilder(word.length());

    for (int i = 0; i < word.length(); ++i) {
      if (MatchUtils.SPECIAL_CHARS.indexOf(word.charAt(i)) != -1) {
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
}
