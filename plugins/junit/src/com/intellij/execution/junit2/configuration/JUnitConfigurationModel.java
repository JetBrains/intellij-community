/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.junit2.configuration;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;

import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

// Author: dyoma

public class JUnitConfigurationModel {
  public static final int ALL_IN_PACKAGE = 0;
  public static final int CLASS = 1;
  public static final int METHOD = 2;
  public static final int PATTERN = 3;
  public static final int DIR = 4;
  public static final int CATEGORY = 5;
  public static final int BY_SOURCE_POSITION = 6;
  public static final int BY_SOURCE_CHANGES = 7;

  private static final List<String> ourTestObjects;

  static {
    ourTestObjects = Arrays.asList(JUnitConfiguration.TEST_PACKAGE,
                                   JUnitConfiguration.TEST_CLASS, 
                                   JUnitConfiguration.TEST_METHOD,
                                   JUnitConfiguration.TEST_PATTERN,
                                   JUnitConfiguration.TEST_DIRECTORY,
                                   JUnitConfiguration.TEST_CATEGORY,
                                   JUnitConfiguration.BY_SOURCE_POSITION,
                                   JUnitConfiguration.BY_SOURCE_CHANGES);
  }


  private JUnitConfigurable myListener;
  private int myType = -1;
  private final Object[] myJUnitDocuments = new Object[6];
  private final Project myProject;

  public JUnitConfigurationModel(final Project project) {
    myProject = project;
  }

  public boolean setType(int type) {
    if (type == myType) return false;
    if (type < 0 || type >= ourTestObjects.size()) type = CLASS;
    myType = type;
    fireTypeChanged(type);
    return true;
  }

  private void fireTypeChanged(final int newType) {
    myListener.onTypeChanged(newType);
  }

  public void setListener(final JUnitConfigurable listener) {
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
      try {
        data.METHOD_NAME = getJUnitTextValue(METHOD);
        final PsiClass testClass = !myProject.isDefault() && !StringUtil.isEmptyOrSpaces(className) ? JUnitUtil.findPsiClass(className, module, myProject) : null;
        if (testClass != null && testClass.isValid()) {
          data.setMainClass(testClass);
        }
        else {
          data.MAIN_CLASS_NAME = className;
        }
      }
      catch (ProcessCanceledException e) {
        data.MAIN_CLASS_NAME = className;
      }
      catch (IndexNotReadyException e) {
        data.MAIN_CLASS_NAME = className;
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
    setJUnitTextValue(CLASS, data.getMainClassName() != null ? data.getMainClassName().replaceAll("\\$", "\\.") : "");
    setJUnitTextValue(METHOD, data.getMethodName());
    setJUnitTextValue(PATTERN, data.getPatternPresentation());
    setJUnitTextValue(DIR, data.getDirName());
    setJUnitTextValue(CATEGORY, data.getCategory());
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
      WriteCommandAction.runWriteCommandAction(myProject, () -> ((Document)document).replaceString(0, ((Document)document).getTextLength(), text));
    }
  }

  private void setTestType(final String testObject) {
    setType(ourTestObjects.indexOf(testObject));
  }
}

