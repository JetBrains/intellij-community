// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.themes.metadata.UIThemeMetadataService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.psi.search.GlobalSearchScope.allScope;

public final class ThemeJsonUtil {
  private static final @NonNls String UI_PROPERTY_NAME = "ui";
  private static final @NonNls String COLORS_PROPERTY_NAME = "colors";
  private static final @NonNls String NAME_PROPERTY_NAME = "name";
  private static final @NonNls String PARENT_THEME_PROPERTY_NAME = "parentTheme";

  private static final Pattern GROUP_MASK_PATTERN = Pattern.compile("(\\.Group[\\d+])");
  private static final Pattern COLOR_MASK_PATTERN = Pattern.compile("(\\.Color[\\d+])");
  private static final Pattern FRACTION_MASK_PATTERN = Pattern.compile("(\\.Fraction[\\d+])");

  private static final String GROUP_N = ".GroupN";
  private static final String COLOR_N = ".ColorN";
  private static final String FRACTION_N = ".FractionN";

  public static final String THEME_PROVIDER_TAG_NAME = "themeProvider";

  static boolean isInsideUiProperty(@NotNull JsonProperty property) {
    PsiElement parent = property;
    while ((parent = parent.getParent()) != null) {
      if (!(parent instanceof JsonProperty)) continue;
      if (UI_PROPERTY_NAME.equals(((JsonProperty)parent).getName())) return true;
    }
    return false;
  }

  static boolean isInsideColors(@NotNull JsonProperty property) {
    PsiElement parent = property.getParent(); // JsonObject
    if (!(parent instanceof JsonObject)) return false;

    PsiElement grandParent = parent.getParent(); // JsonProperty or JsonFile
    if (!(grandParent instanceof JsonProperty topLevelProperty)) return false;

    if (!COLORS_PROPERTY_NAME.equals(topLevelProperty.getName())) return false;
    return topLevelProperty.getParent() instanceof JsonObject topLevelObject &&
           topLevelObject.getParent() instanceof JsonFile;
  }

  static String getParentNames(@NotNull JsonProperty property) {
    List<JsonProperty> parentProperties = PsiTreeUtil.collectParents(property, JsonProperty.class, false, e -> {
      //TODO check that it is TOP-LEVEL 'ui'  property
      return e instanceof JsonProperty && UI_PROPERTY_NAME.equals(((JsonProperty)e).getName());
    });

    return ContainerUtil.reverse(parentProperties).stream()
      .map(p -> p.getName())
      .collect(Collectors.joining("."));
  }

  public static boolean isThemeFilename(@NotNull String fileName) {
    return StringUtil.endsWithIgnoreCase(fileName, ".theme.json");
  }

  static Map<String, ColorValueDefinition> getNamedColorsMap(@NotNull JsonFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      Map<String, ColorValueDefinition> namedColorsMap = new HashMap<>();
      List<JsonProperty> colors = collectNamedColors(file);
      for (JsonProperty property : colors) {
        JsonValue value = property.getValue();
        if (value instanceof JsonStringLiteral) {
          namedColorsMap.put(property.getName(),
                             new ColorValueDefinition(property.getName(), ((JsonStringLiteral)value).getValue(), PsiAnchor.create(property)));
        }
      }
      return Result.create(Map.copyOf(namedColorsMap), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  static Map<String, ColorValueDefinition> getParentNamedColorsMap(@NotNull JsonFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      Map<String, ColorValueDefinition> namedColorsMap = new HashMap<>();
      List<JsonProperty> colors = collectParentNamedColors(file);
      for (JsonProperty property : colors) {
        JsonValue value = property.getValue();
        if (value instanceof JsonStringLiteral) {
          namedColorsMap.put(property.getName(),
                             new ColorValueDefinition(property.getName(), ((JsonStringLiteral)value).getValue(), PsiAnchor.create(property)));
        }
      }
      return Result.create(Map.copyOf(namedColorsMap), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static List<JsonProperty> collectNamedColors(@NotNull JsonFile themeFile) {
    List<JsonProperty> result = new ArrayList<>();
    collectNamedColors(themeFile, result, new SmartList<>());
    return result;
  }

  private static List<JsonProperty> collectParentNamedColors(@NotNull JsonFile themeFile) {
    if (DumbService.isDumb(themeFile.getProject())) return List.of(); // may be hit from usages highlighting during indexing

    List<JsonProperty> result = new ArrayList<>();
    JsonValue topLevelValue = themeFile.getTopLevelValue();
    if (topLevelValue instanceof JsonObject topLevelObject) {
      JsonProperty parentThemeProperty = topLevelObject.findProperty(PARENT_THEME_PROPERTY_NAME);
      if (parentThemeProperty != null && parentThemeProperty.getValue() instanceof JsonStringLiteral parentThemeLiteral) {
        String parentThemeName = parentThemeLiteral.getValue();
        JsonFile parentThemeFile = findThemeFile(themeFile.getProject(), parentThemeName);
        if (parentThemeFile != null) {
          collectNamedColors(parentThemeFile, result, new SmartList<>());
        }
      }
    }
    return result;
  }

  private static void collectNamedColors(@NotNull JsonFile themeFile,
                                         @NotNull List<JsonProperty> result,
                                         @NotNull List<JsonFile> visited) {
    if (visited.contains(themeFile)) return;
    visited.add(themeFile);

    JsonValue topLevelValue = themeFile.getTopLevelValue();
    if (!(topLevelValue instanceof JsonObject topLevelObject)) return;

    JsonProperty parentThemeProperty = topLevelObject.findProperty(PARENT_THEME_PROPERTY_NAME);
    if (parentThemeProperty != null && parentThemeProperty.getValue() instanceof JsonStringLiteral parentThemeLiteral) {
      String parentThemeName = parentThemeLiteral.getValue();
      JsonFile parentThemeFile = findThemeFile(themeFile.getProject(), parentThemeName);
      if (parentThemeFile != null) {
        collectNamedColors(parentThemeFile, result, visited);
      }
    }

    JsonProperty colorsProperty = topLevelObject.findProperty(COLORS_PROPERTY_NAME);
    if (colorsProperty != null && colorsProperty.getValue() instanceof JsonObject colorsObject) {
      result.addAll(colorsObject.getPropertyList());
    }
  }

  private static @Nullable JsonFile findThemeFile(@NotNull Project project, @NotNull String themeNameOrId) {
    if (DumbService.isDumb(project)) return null; // may be hit from usages highlighting during indexing
    if (StringUtil.isEmptyOrSpaces(themeNameOrId)) return null;

    JsonFile fileById = findThemeFileByProviderId(project, themeNameOrId);
    if (fileById != null) return fileById;

    GlobalSearchScope scope = allScope(project);
    return FilenameIndex.getVirtualFilesByName(themeNameOrId + ".theme.json", scope).stream()
      .map(file -> {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        return psiFile instanceof JsonFile ? (JsonFile)psiFile : null;
      })
      .filter(Objects::nonNull)
      .findFirst()
      .orElseGet(() -> {
        // Try searching by theme name inside the file if filename doesn't match
        return FilenameIndex.getAllFilesByExt(project, "theme.json", scope).stream()
          .map(file -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            return psiFile instanceof JsonFile ? (JsonFile)psiFile : null;
          })
          .filter(jsonFile -> jsonFile != null && themeNameOrId.equals(getThemeName(jsonFile)))
          .findFirst().orElse(null);
      });
  }

  private static @Nullable JsonFile findThemeFileByProviderId(@NotNull Project project, @NotNull String themeId) {
    var cacheManager = CacheManager.getInstance(project);
    // there are tons of module and plugin XML files, find small number of file candidates first
    var files = cacheManager.getVirtualFilesWithWord(THEME_PROVIDER_TAG_NAME, UsageSearchContext.IN_FOREIGN_LANGUAGES, allScope(project), true);
    var fileTypeManager = FileTypeManager.getInstance();
    var xmlFilesWithWord = Arrays.stream(files)
      .filter(file -> fileTypeManager.isFileOfType(file, XmlFileType.INSTANCE))
      .collect(Collectors.toSet());

    var smallScope = GlobalSearchScope.filesScope(project, xmlFilesWithWord);

    var domManager = DomManager.getDomManager(project);
    for (var pluginXml : DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, smallScope)) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(pluginXml);
      if (!(psiFile instanceof XmlFile)) continue;

      var fileElement = domManager.getFileElement((XmlFile)psiFile, IdeaPlugin.class);
      if (fileElement == null) continue;

      IdeaPlugin ideaPlugin = fileElement.getRootElement();
      for (Extensions extensions : ideaPlugin.getExtensions()) {
        for (Extension extension : extensions.collectExtensions()) {
          XmlTag tag = extension.getXmlTag();
          if (THEME_PROVIDER_TAG_NAME.equals(tag.getName())) {
            String id = tag.getAttributeValue("id");
            if (themeId.equals(id)) {
              String path = tag.getAttributeValue("path");
              if (path != null) {
                VirtualFile themeFile = findFileByRelativePath(pluginXml, path);
                if (themeFile != null) {
                  PsiFile themePsiFile = PsiManager.getInstance(project).findFile(themeFile);
                  if (themePsiFile instanceof JsonFile) {
                    return (JsonFile)themePsiFile;
                  }
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static @Nullable VirtualFile findFileByRelativePath(@NotNull VirtualFile baseFile, @NotNull String path) {
    VirtualFile parent = baseFile.getParent();
    if (parent == null) return null;

    path = StringUtil.trimStart(path, "/");

    if ("META-INF".equals(parent.getName())) {
      VirtualFile root = parent.getParent();
      if (root != null) {
        VirtualFile found = root.findFileByRelativePath(path);
        if (found != null) return found;
      }
    }
    return parent.findFileByRelativePath(path);
  }

  private static @Nullable String getThemeName(@NotNull JsonFile themeFile) {
    JsonValue topLevelValue = themeFile.getTopLevelValue();
    if (topLevelValue instanceof JsonObject topLevelObject) {
      JsonProperty nameProperty = topLevelObject.findProperty(NAME_PROPERTY_NAME);
      if (nameProperty != null && nameProperty.getValue() instanceof JsonStringLiteral nameLiteral) {
        return nameLiteral.getValue();
      }
    }
    return null;
  }

  static @Nullable Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> findMetadata(@NotNull JsonProperty property) {
    String key = property.getName();

    Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> byName = UIThemeMetadataService.getInstance().findByKey(key);
    if (byName != null) return byName;

    String fullKey = getParentNames(property) + "." + key;
    if (looksLikeNumberedMetaKey(fullKey)) {
      fullKey = GROUP_MASK_PATTERN.matcher(fullKey).replaceAll(GROUP_N);
      fullKey = FRACTION_MASK_PATTERN.matcher(fullKey).replaceAll(FRACTION_N);
      fullKey = COLOR_MASK_PATTERN.matcher(fullKey).replaceAll(COLOR_N);
    }

    return UIThemeMetadataService.getInstance().findByKey(fullKey);
  }

  private static boolean looksLikeNumberedMetaKey(String key) {
    return key.contains(".Group")
           || key.contains(".Color")
           || key.contains(".Fraction");
  }
}