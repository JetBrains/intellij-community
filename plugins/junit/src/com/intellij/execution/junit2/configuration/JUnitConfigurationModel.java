// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit2.configuration;

import com.intellij.execution.JUnitBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiConsumer;

// Author: dyoma

public class JUnitConfigurationModel {
  public static final int ALL_IN_PACKAGE = 0;
  public static final int CLASS = 1;
  public static final int METHOD = 2;
  public static final int PATTERN = 3;
  public static final int DIR = 4;
  public static final int CATEGORY = 5;
  public static final int UNIQUE_ID = 6;
  public static final int TAGS = 7;
  public static final int BY_SOURCE_POSITION = 8;
  public static final int BY_SOURCE_CHANGES = 9;

  private static final List<String> ourTestObjects;

  static {
    ourTestObjects = Arrays.asList(JUnitConfiguration.TEST_PACKAGE,
                                   JUnitConfiguration.TEST_CLASS, 
                                   JUnitConfiguration.TEST_METHOD,
                                   JUnitConfiguration.TEST_PATTERN,
                                   JUnitConfiguration.TEST_DIRECTORY,
                                   JUnitConfiguration.TEST_CATEGORY,
                                   JUnitConfiguration.TEST_UNIQUE_ID,
                                   JUnitConfiguration.TEST_TAGS,
                                   JUnitConfiguration.BY_SOURCE_POSITION,
                                   JUnitConfiguration.BY_SOURCE_CHANGES);
  }


  private BiConsumer<Integer, Integer> myListener;
  private int myType = -1;
  private final Object[] myJUnitDocuments = new Object[6];
  private final Project myProject;

  public JUnitConfigurationModel(final Project project) {
    myProject = project;
  }

  public boolean setType(int type) {
    if (type == myType) return false;
    int oldType = myType;
    if (type < 0 || type >= ourTestObjects.size()) type = CLASS;
    myType = type;
    fireTypeChanged(oldType, type);
    return true;
  }

  private void fireTypeChanged(final int oldType, final int newType) {
    myListener.accept(oldType, newType);
  }

  public void setListener(final BiConsumer<Integer, Integer> listener) {
    myListener = listener;
  }

  public Object getJUnitDocument(final int i) {
    return myJUnitDocuments[i];
  }

  public void setJUnitDocument(final int i, Object doc) {
     myJUnitDocuments[i] = doc;
  }

  public void apply(final Module module, final JUnitConfiguration configuration) {
    final boolean shouldUpdateName = configuration.isGeneratedName();
    applyTo(configuration.getPersistentData(), module);
    if (shouldUpdateName && !JavaExecutionUtil.isNewName(configuration.getName())) {
      configuration.setGeneratedName();
    }
  }

  private void applyTo(final JUnitConfiguration.Data data, final Module module) {
    final String testObject = getTestObject();
    final String className = getJUnitTextValue(CLASS);
    data.TEST_OBJECT = testObject;
    if (testObject != JUnitConfiguration.TEST_PACKAGE &&
        testObject != JUnitConfiguration.TEST_PATTERN &&
        testObject != JUnitConfiguration.TEST_DIRECTORY &&
        testObject != JUnitConfiguration.TEST_CATEGORY  &&
        testObject != JUnitConfiguration.BY_SOURCE_CHANGES) {
      data.METHOD_NAME = getJUnitTextValue(METHOD);
      if (!className.equals(replaceRuntimeClassName(data.getMainClassName()))) {
        try {
          final PsiClass testClass;
          if (!myProject.isDefault() && !StringUtil.isEmptyOrSpaces(className)) {
            JavaRunConfigurationModule configurationModule = new JavaRunConfigurationModule(myProject, true);
            configurationModule.setModule(module);
            testClass = configurationModule.findClass(className);
          }
          else {
            testClass = null;
          }
          if (testClass != null && testClass.isValid()) {
            data.setMainClass(testClass);
          }
          else {
            data.MAIN_CLASS_NAME = className;
          }
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          data.MAIN_CLASS_NAME = className;
        }
      }
    }
    else if (testObject != JUnitConfiguration.BY_SOURCE_CHANGES) {
      if (testObject == JUnitConfiguration.TEST_PACKAGE) {
        data.PACKAGE_NAME = getJUnitTextValue(ALL_IN_PACKAGE);
      }
      else if (testObject == JUnitConfiguration.TEST_DIRECTORY) {
        data.setDirName(getJUnitTextValue(DIR));
      }
      else if (testObject == JUnitConfiguration.TEST_CATEGORY) {
        data.setCategoryName(getJUnitTextValue(CATEGORY));
      }
      else {
        final LinkedHashSet<String> set = new LinkedHashSet<>();
        final String[] patterns = getJUnitTextValue(PATTERN).split("\\|\\|");
        for (String pattern : patterns) {
          if (pattern.length() > 0) {
            set.add(pattern);
          }
        }
        data.setPatterns(set);
      }
      data.MAIN_CLASS_NAME = "";
      data.METHOD_NAME = "";
    }
  }

  private String getTestObject() {
    return ourTestObjects.get(myType);
  }

  private String getJUnitTextValue(final int index) {
    return getDocumentText(index, myJUnitDocuments);
  }

  private static String getDocumentText(final int index, final Object[] documents) {
    final Object document = documents[index];
    if (document instanceof PlainDocument) {
      try {
        return ((PlainDocument)document).getText(0, ((PlainDocument)document).getLength());
      }
      catch (BadLocationException e) {
        throw new RuntimeException(e);
      }
    }
    return ((Document)document).getText();
  }

  public void reset(final JUnitConfiguration configuration) {
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    setTestType(data.TEST_OBJECT);
    setJUnitTextValue(ALL_IN_PACKAGE, data.getPackageName());
    setJUnitTextValue(CLASS, replaceRuntimeClassName(data.getMainClassName()));
    setJUnitTextValue(METHOD, data.getMethodNameWithSignature());
    setJUnitTextValue(PATTERN, data.getPatternPresentation());
    setJUnitTextValue(DIR, data.getDirName());
    setJUnitTextValue(CATEGORY, data.getCategory());
  }

  private static String replaceRuntimeClassName(String mainClassName) {
    return mainClassName.replaceAll("\\$", "\\.");
  }

  private void setJUnitTextValue(final int index, final String text) {
    setDocumentText(index, text, myJUnitDocuments);
  }

  private void setDocumentText(final int index, final String text, final Object[] documents) {
    final Object document = documents[index];
    if (document instanceof PlainDocument) {
      try {
        ((PlainDocument)document).remove(0, ((PlainDocument)document).getLength());
        ((PlainDocument)document).insertString(0, text, null);
      }
      catch (BadLocationException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      WriteCommandAction
        .runWriteCommandAction(myProject, () -> ((Document)document).replaceString(0, ((Document)document).getTextLength(), text));
    }
  }

  private void setTestType(final String testObject) {
    setType(ourTestObjects.indexOf(testObject));
  }

  public static @NotNull @NlsContexts.Label String getKindName(int value) {
    switch (value) {
      case ALL_IN_PACKAGE:
        return JUnitBundle.message("junit.configuration.kind.all.in.package");
      case DIR:
        return JUnitBundle.message("junit.configuration.kind.all.in.directory");
      case PATTERN:
        return JUnitBundle.message("junit.configuration.kind.by.pattern");
      case CLASS:
        return JUnitBundle.message("junit.configuration.kind.class");
      case METHOD:
        return JUnitBundle.message("junit.configuration.kind.method");
      case CATEGORY:
        return JUnitBundle.message("junit.configuration.kind.category");
      case UNIQUE_ID:
        return JUnitBundle.message("junit.configuration.kind.by.unique.id");
      case TAGS:
        return JUnitBundle.message("junit.configuration.kind.by.tags");
      case BY_SOURCE_POSITION:
        return "Through source location"; //NON-NLS internal option
      case BY_SOURCE_CHANGES:
        return "Over changes in sources"; //NON-NLS internal option
    }
    throw new IllegalArgumentException(String.valueOf(value));
  }

  public static @NotNull @NlsContexts.Label String getRepeatModeName(@NotNull @NonNls String value) {
    switch (value) {
      case RepeatCount.ONCE:
        return JUnitBundle.message("junit.configuration.repeat.mode.once");
      case RepeatCount.N:
        return JUnitBundle.message("junit.configuration.repeat.mode.n.times");
      case RepeatCount.UNTIL_FAILURE:
        return JUnitBundle.message("junit.configuration.repeat.mode.until.failure");
      case RepeatCount.UNLIMITED:
        return JUnitBundle.message("junit.configuration.repeat.mode.until.stopped");
    }

    throw new IllegalArgumentException(value);
  }

  public static @NotNull @NlsContexts.Label String getForkModeName(@NotNull @NonNls String value) {
    switch (value) {
      case JUnitConfiguration.FORK_NONE:
        return JUnitBundle.message("junit.configuration.fork.mode.none");
      case JUnitConfiguration.FORK_METHOD:
        return JUnitBundle.message("junit.configuration.fork.mode.method");
      case JUnitConfiguration.FORK_KLASS:
        return JUnitBundle.message("junit.configuration.fork.mode.class");
      case JUnitConfiguration.FORK_REPEAT:
        return JUnitBundle.message("junit.configuration.fork.mode.repeat");
    }

    throw new IllegalArgumentException(value);
  }

  public void reloadTestKindModel(JComboBox<Integer> comboBox, Module module) {
    int selectedIndex = comboBox.getSelectedIndex();
    final DefaultComboBoxModel<Integer> aModel = new DefaultComboBoxModel<>();
    aModel.addElement(ALL_IN_PACKAGE);
    aModel.addElement(DIR);
    aModel.addElement(PATTERN);
    aModel.addElement(CLASS);
    aModel.addElement(METHOD);

    GlobalSearchScope searchScope = module != null ? GlobalSearchScope.moduleRuntimeScope(module, true)
                                                   : GlobalSearchScope.allScope(myProject);

    if (myProject.isDefault() || JavaPsiFacade.getInstance(myProject).findPackage("org.junit") != null) {
      aModel.addElement(CATEGORY);
    }

    if (myProject.isDefault() ||
        JUnitUtil.isJUnit5(searchScope, myProject) ||
        TestObject.hasJUnit5EnginesAPI(searchScope, JavaPsiFacade.getInstance(myProject))) {
      aModel.addElement(UNIQUE_ID);
      aModel.addElement(TAGS);
    }

    if (Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY)) {
      aModel.addElement(BY_SOURCE_POSITION);
      aModel.addElement(BY_SOURCE_CHANGES);
    }
    comboBox.setModel(aModel);
    comboBox.setSelectedIndex(selectedIndex);
  }

  public boolean disableModuleClasspath(boolean wholeProjectSelected) {
    return wholeProjectSelected && (myType == ALL_IN_PACKAGE ||
                                    myType == PATTERN ||
                                    myType == CATEGORY ||
                                    myType == TAGS ||
                                    myType == UNIQUE_ID);
  }
}

