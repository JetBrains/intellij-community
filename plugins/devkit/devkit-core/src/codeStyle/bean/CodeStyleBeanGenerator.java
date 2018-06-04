// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeStyle.bean;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CodeStyleBeanGenerator {
  private final PsiFile myFile;
  private final PsiClass myBeanClass;
  private final Language myLanguage;
  private final Set<String> myImports = ContainerUtil.newHashSet();

  public CodeStyleBeanGenerator(@NotNull PsiFile file, @NotNull PsiClass aClass, @NotNull Language language) {
    myFile = file;
    myBeanClass = aClass;
    myLanguage = language;
  }

  public String generateBeanMethods() {
    StringBuilder sb = new StringBuilder();
    generateMethodsFor(CommonCodeStyleSettings.IndentOptions.class, sb, getSupportedIndentOptions());
    generateMethodsFor(CommonCodeStyleSettings.class, sb, getSupportedFields());
    for (CustomCodeStyleSettings customSettings : getCustomSettings()) {
      generateMethodsFor(customSettings.getClass(), sb, null);
    }
    return sb.toString();
  }

  private Set<String> getSupportedIndentOptions() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    if (provider == null) return Collections.emptySet();
    Set<String> indentOptions = ContainerUtil.newHashSet();
    IndentOptionsEditor editor = provider.getIndentOptionsEditor();
    if (editor != null) {
      indentOptions.add("TAB_SIZE");
      indentOptions.add("USE_TAB_CHARACTER");
      indentOptions.add("INDENT_SIZE");
      if (editor instanceof SmartIndentOptionsEditor) {
        indentOptions.add("CONTINUATION_INDENT_SIZE");
        indentOptions.add("SMART_TABS");
        indentOptions.add("KEEP_INDENTS_ON_EMPTY_LINES");
      }
    }
    return indentOptions;
  }

  private Set<String> getSupportedFields() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    return provider == null ? Collections.emptySet() : provider.getSupportedFields();
  }

  private void generateMethodsFor(Class codeStyleClass, StringBuilder output, @Nullable Set<String> supportedFields) {
    for (Field field : getCodeStyleFields(codeStyleClass)) {
      CodeStyleBeanAccessorGenerator accessorGenerator = new CodeStyleBeanAccessorGenerator(field);
      String fieldName = field.getName();
      if ((supportedFields == null || supportedFields.contains(fieldName)) &&
          !beanContainsField(fieldName) &&
          accessorGenerator.isFieldSupported()) {
        String setterName = accessorGenerator.getSetterName();
        if (!superClassContainsMethod(setterName)) {
          accessorGenerator.generateGetter(output);
        }
        String getterName = accessorGenerator.getGetterName();
        if (!superClassContainsMethod(getterName)) {
          accessorGenerator.generateSetter(output);
        }
      }
      myImports.addAll(accessorGenerator.getImports());
    }
  }

  private static List<Field> getCodeStyleFields(Class codeStyleClass) {
    List<Field> fields = new ArrayList<>();
    for (Field field : codeStyleClass.getFields()) {
      if (isPublic(field) && !isFinal(field)) {
        fields.add(field);
      }
    }
    return fields;
  }

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

  private boolean beanContainsField(@NotNull String fieldName) {
    PsiMethod[] methods = myBeanClass.getMethods();
    for (PsiMethod method : methods) {
      if (methodContainsField(method, fieldName)) return true;
    }
    return false;
  }

  private boolean superClassContainsMethod(@NotNull String methodName) {
    PsiClass superClass = myBeanClass.getSuperClass();
    if (superClass != null) {
      for (PsiMethod baseMethod : superClass.getMethods()) {
        if (methodName.equals(baseMethod.getName())) return true;
      }
    }
    return false;
  }

  private static boolean methodContainsField(@NotNull PsiMethod method, @NotNull String fieldName) {
    final Ref<Boolean> found = Ref.create(false);
    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof PsiIdentifier && fieldName.equals(element.getText())) {
          found.set(true);
        }
        else {
          super.visitElement(element);
        }
      }
    };
    method.accept(visitor);
    return found.get();
  }

  private List<CustomCodeStyleSettings> getCustomSettings() {
    CodeStyleSettings rootSettings = new CodeStyleSettings();
    List<CustomCodeStyleSettings> customSettingsList = new ArrayList<>();
    for (CodeStyleSettingsProvider provider : Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME)) {
      if (provider.getLanguage() == myLanguage) {
        CustomCodeStyleSettings customSettings = provider.createCustomSettings(rootSettings);
        if (customSettings != null && resolveClass(customSettings.getClass().getName(), myFile) != null) {
          customSettingsList.add(customSettings);
        }
      }
    }
    return customSettingsList;
  }


  @Nullable
  static PsiClass resolveClass(@NotNull String classFQN, @NotNull PsiFile file) {
    PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(file.getProject());
    return resolveHelper.resolveReferencedClass(classFQN, file);
  }

  Set<String> getImports() {
    return myImports;
  }
}
