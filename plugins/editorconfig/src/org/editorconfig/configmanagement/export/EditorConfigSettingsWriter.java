// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.export;

import com.intellij.application.options.codeStyle.properties.*;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.editorconfig.configmanagement.extended.EditorConfigIntellijNameUtil;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.editorconfig.configmanagement.extended.IntellijPropertyKindMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.editorconfig.core.EditorConfig.OutPair;

public class EditorConfigSettingsWriter extends OutputStreamWriter {
  private final           CodeStyleSettings        mySettings;
  private final @Nullable Project                  myProject;

  // region Filters
  private Set<Language>                       myLanguages;
  private final Set<EditorConfigPropertyKind> myPropertyKinds = EnumSet.allOf(EditorConfigPropertyKind.class);
  // endregion

  public EditorConfigSettingsWriter(@Nullable Project project, @NotNull OutputStream out, CodeStyleSettings settings) {
    super(out, StandardCharsets.UTF_8);
    mySettings = settings;
    myProject = project;
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

  public void writeSettings() throws IOException {
    final List<AbstractCodeStylePropertyMapper> mappers = new ArrayList<>();
    writeGeneralSection(new GeneralCodeStylePropertyMapper(mySettings));
    CodeStylePropertiesUtil.collectMappers(mySettings, mapper -> mappers.add(mapper));
    for (AbstractCodeStylePropertyMapper mapper : mappers) {
      writeLangSection(mapper);
    }
  }

  private void writeGeneralSection(@NotNull GeneralCodeStylePropertyMapper mapper) throws IOException {
    write("[*]\n");
    if (myPropertyKinds.contains(EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD)) {
      if (myProject != null) {
        write(Utils.getEncoding(myProject));
      }
      String lineSeparator = Utils.getLineSeparatorString(mySettings.getLineSeparator());
      if (lineSeparator != null) {
        write(LineEndingsManager.lineEndingsKey + " = " + lineSeparator + "\n");
      }
      write(Utils.getEndOfFile());
      write(Utils.getTrailingSpaces());
    }
    writeProperties(getKeyValuePairs(mapper));
  }

  private void writeLangSection(@NotNull AbstractCodeStylePropertyMapper mapper) throws IOException {
    if (mapper instanceof LanguageCodeStylePropertyMapper) {
      Language language = ((LanguageCodeStylePropertyMapper)mapper).getLanguage();
      if (myLanguages == null || myLanguages.contains(language)) {
        FileType fileType = language.getAssociatedFileType();
        if (fileType != null) {
          List<OutPair> optionValueList = getKeyValuePairs(mapper);
          if (!optionValueList.isEmpty()) {
            write("\n[" + Utils.buildPattern(fileType) + "]\n");
            writeProperties(optionValueList);
          }
        }
      }
    }
  }

  private List<OutPair> getKeyValuePairs(@NotNull AbstractCodeStylePropertyMapper mapper) {
    List<OutPair> optionValueList = new ArrayList<>();
    for (String property : orderOptions(mapper.enumProperties())) {
      CodeStylePropertyAccessor accessor = mapper.getAccessor(property);
      EditorConfigPropertyKind propertyKind = IntellijPropertyKindMap.getPropertyKind(property);
      boolean isIntelliJProperty = !propertyKind.equals(EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD);
      if (
        isIntelliJProperty && myPropertyKinds.contains(EditorConfigPropertyKind.LANGUAGE) ||
        !isIntelliJProperty && myPropertyKinds.contains(EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD)
      ) {
        String name = getEditorConfigName(mapper, property);
        if (name != null) {
          String value = accessor.getAsString();
          if (value != null && !value.trim().isEmpty() && isAllowed(value)) {
            optionValueList.add(new OutPair(name, value));
          }
        }
      }
    }
    return optionValueList;
  }

  private static boolean isAllowed(@NotNull String value) {
    // TODO<rv> REMOVE THE HACK
    //  EditorConfig implementation doesn't allow dots. We need to skip such values till the parser issue is fixed.
    return !value.contains(".");
  }

  private void writeProperties(@NotNull List<OutPair> outPairs) throws IOException {
    for (OutPair pair : outPairs) {
      write(pair.getKey() + " = " + pair.getVal() + "\n");
    }
  }

  private static List<String> orderOptions(@NotNull List<String> propertyList) {
    Collections.sort(propertyList, (name1, name2) -> {
      EditorConfigPropertyKind pKind1 = IntellijPropertyKindMap.getPropertyKind(name1);
      EditorConfigPropertyKind pKind2 = IntellijPropertyKindMap.getPropertyKind(name2);
      if (!pKind1.equals(pKind2)) return Comparing.compare(pKind2, pKind1); // in reversed order
      return Comparing.compare(name1, name2);
    });
    return propertyList;
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
