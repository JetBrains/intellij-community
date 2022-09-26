// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.export;

import com.intellij.application.options.codeStyle.properties.*;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.containers.MultiMap;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.ConfigEncodingManager;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.editorconfig.configmanagement.StandardEditorConfigProperties;
import org.editorconfig.configmanagement.extended.EditorConfigIntellijNameUtil;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.editorconfig.configmanagement.extended.EditorConfigValueUtil;
import org.editorconfig.configmanagement.extended.IntellijPropertyKindMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.editorconfig.core.EditorConfig.OutPair;

public class EditorConfigSettingsWriter extends OutputStreamWriter {
  private final           CodeStyleSettings   mySettings;
  private final @Nullable Project             myProject;
  private final           Map<String, String> myGeneralOptions = new HashMap<>();
  private final           boolean             myAddRootFlag;
  private final           boolean             myCommentOutProperties;

  private boolean myNoHeaders;

  private final static Comparator<OutPair> PAIR_COMPARATOR = (pair1, pair2) -> {
    EditorConfigPropertyKind pKind1 = getPropertyKind(pair1.getKey());
    EditorConfigPropertyKind pKind2 = getPropertyKind(pair2.getKey());
    if (!pKind1.equals(pKind2)) return Comparing.compare(pKind2, pKind1); // in reversed order
    return Comparing.compare(pair1.getKey(), pair2.getKey());
  };

  // region Filters
  private Set<Language>                       myLanguages;
  private final Set<EditorConfigPropertyKind> myPropertyKinds = EnumSet.allOf(EditorConfigPropertyKind.class);
  // endregion

  public EditorConfigSettingsWriter(@Nullable Project project,
                                    @NotNull OutputStream out,
                                    CodeStyleSettings settings,
                                    boolean isRoot,
                                    boolean commentOutProperties) {
    super(out, StandardCharsets.UTF_8);
    mySettings = settings;
    myProject = project;
    myAddRootFlag = isRoot;
    myCommentOutProperties = commentOutProperties;
    fillGeneralOptions();
  }

  private void fillGeneralOptions() {
    for (OutPair pair : getKeyValuePairs(new GeneralCodeStylePropertyMapper(mySettings))) {
      myGeneralOptions.put(pair.getKey(), pair.getVal());
    }
    myGeneralOptions.put("ij_continuation_indent_size", String.valueOf(mySettings.OTHER_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE));
    if (myProject != null) {
      String encoding = Utils.getEncoding(myProject);
      if (encoding != null) {
        myGeneralOptions.put(ConfigEncodingManager.charsetKey, encoding);
      }
    }
    String lineSeparator = Utils.getLineSeparatorString(mySettings.getLineSeparator());
    if (lineSeparator != null) {
      myGeneralOptions.put(LineEndingsManager.lineEndingsKey, lineSeparator);
    }
    myGeneralOptions.put(StandardEditorConfigProperties.INSERT_FINAL_NEWLINE,
                         String.valueOf(EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF()));
    Boolean trimSpaces = Utils.getTrimTrailingSpaces();
    if (trimSpaces != null) {
      myGeneralOptions.put(StandardEditorConfigProperties.TRIM_TRAILING_WHITESPACE, String.valueOf(trimSpaces));
    }
  }

  public EditorConfigSettingsWriter forLanguages(List<Language> languages) {
    myLanguages = new HashSet<>(languages.size());
    myLanguages.addAll(languages);
    return this;
  }

  public EditorConfigSettingsWriter forLanguages(Language... languages) {
    myLanguages = new HashSet<>(languages.length);
    myLanguages.addAll(Arrays.asList(languages));
    return this;
  }

  public EditorConfigSettingsWriter forPropertyKinds(EditorConfigPropertyKind... kinds) {
    myPropertyKinds.clear();
    myPropertyKinds.addAll(Arrays.asList(kinds));
    return this;
  }

  public EditorConfigSettingsWriter withoutHeaders() {
    myNoHeaders = true;
    return this;
  }

  public void writeSettings() throws IOException {
    if (myAddRootFlag) {
      writeProperties(Collections.singletonList(new OutPair("root", "true")), false);
      write("\n");
    }
    writeGeneralSection();
    final MultiMap<String,LanguageCodeStylePropertyMapper> mappers = new MultiMap<>();
    CodeStylePropertiesUtil.collectMappers(mySettings, mapper -> {
      if (mapper instanceof LanguageCodeStylePropertyMapper) {
        FileType fileType = ((LanguageCodeStylePropertyMapper)mapper).getLanguage().getAssociatedFileType();
        if (fileType != null) {
          String pattern = Utils.buildPattern(fileType);
          mappers.putValue(pattern, (LanguageCodeStylePropertyMapper)mapper);
        }
      }
    });
    for (String pattern : mappers.keySet().stream().sorted().toList()) {
      if (pattern.isEmpty()) continue;
      String currPattern = pattern;
      for (
        LanguageCodeStylePropertyMapper mapper :
        mappers.get(pattern).stream()
          .sorted(Comparator.comparing(mapper -> mapper.getLanguageDomainId())).toList()) {
        if (writeLangSection(mapper, currPattern)) {
          currPattern = null; // Do not write again
        }
      }
    }
  }

  private void writeGeneralSection() throws IOException {
    if (!myNoHeaders) {
      write("[*]\n");
    }
    List<OutPair> pairs = myGeneralOptions.keySet().stream()
      .map(key -> new OutPair(key, myGeneralOptions.get(key)))
      .filter(pair -> isNameAllowed(pair.getKey()))
      .sorted(PAIR_COMPARATOR).collect(Collectors.toList());
   writeProperties(pairs, myCommentOutProperties);
  }

  private boolean writeLangSection(@NotNull LanguageCodeStylePropertyMapper mapper, @Nullable String pattern) throws IOException {
    Language language = mapper.getLanguage();
    if (myLanguages == null || myLanguages.contains(language)) {
      List<OutPair> optionValueList = getKeyValuePairs(mapper);
      if (!optionValueList.isEmpty()) {
        if (pattern != null && !myNoHeaders) {
          write("\n[" + pattern + "]\n");
        }
        optionValueList.sort(PAIR_COMPARATOR);
        writeProperties(optionValueList, myCommentOutProperties);
        return true;
      }
    }
    return false;
  }

  private List<OutPair> getKeyValuePairs(@NotNull AbstractCodeStylePropertyMapper mapper) {
    List<OutPair> optionValueList = new ArrayList<>();
    for (String property : mapper.enumProperties()) {
      CodeStylePropertyAccessor accessor = mapper.getAccessor(property);
      String name = getEditorConfigName(mapper, property);
      if (isNameAllowed(name)) {
        String value = getEditorConfigValue(accessor);
        if (isValueAllowed(value) && (!(mapper instanceof LanguageCodeStylePropertyMapper && matchesGeneral(name, value)))) {
          optionValueList.add(new OutPair(name, value));
        }
      }
    }
    return optionValueList;
  }

  private static String getEditorConfigValue(@NotNull CodeStylePropertyAccessor<?> accessor) {
    String value = accessor.getAsString();
    if ((value == null || value.isEmpty()) && CodeStylePropertiesUtil.isAccessorAllowingEmptyList(accessor)) {
      return EditorConfigValueUtil.EMPTY_LIST_VALUE;
    }
    return value;
  }

  private boolean matchesGeneral(@NotNull String name, @NotNull String value) {
    String generalValue = myGeneralOptions.get(name);
    return generalValue != null && generalValue.equals(value);
  }

  private boolean isNameAllowed(@Nullable String ecName) {
    if (ecName != null) {
      return myPropertyKinds.contains(getPropertyKind(ecName));
    }
    return false;
  }

  private static EditorConfigPropertyKind getPropertyKind(@NotNull String ecName) {
    String ijName = EditorConfigIntellijNameUtil.toIntellijName(ecName);
    return IntellijPropertyKindMap.getPropertyKind(ijName);
  }

  private static boolean isValueAllowed(@Nullable String value) {
    return value != null && !value.trim().isEmpty();
  }

  private void writeProperties(@NotNull List<OutPair> outPairs, boolean commentOut) throws IOException {
    for (OutPair pair : outPairs) {
      if (commentOut) {
        write("# ");
      }
      write(pair.getKey() + " = " + pair.getVal() + "\n");
    }
  }

  @Nullable
  private static String getEditorConfigName(@NotNull AbstractCodeStylePropertyMapper mapper, @NotNull String propertyName) {
    List<String> editorConfigNames = EditorConfigIntellijNameUtil.toEditorConfigNames(mapper, propertyName);
    if (editorConfigNames.isEmpty()) {
      return null;
    }
    else if (editorConfigNames.size() == 1) {
      return editorConfigNames.get(0);
    }
    else {
      return EditorConfigIntellijNameUtil.getLanguageProperty(mapper, propertyName);
    }
  }
}
