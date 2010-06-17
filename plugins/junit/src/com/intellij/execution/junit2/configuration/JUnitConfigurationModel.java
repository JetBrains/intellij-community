/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
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

  private static final List<String> ourTestObjects;
  static {
    ourTestObjects = Arrays.asList(JUnitConfiguration.TEST_PACKAGE, JUnitConfiguration.TEST_CLASS, JUnitConfiguration.TEST_METHOD, JUnitConfiguration.TEST_PATTERN);
  }


  private JUnitConfigurable myListener;
  private int myType = -1;
  private final Document[] myJUnitDocuments = new Document[4];
  private final Project myProject;

  public JUnitConfigurationModel(final Project project) {
    for (int i = 0; i < myJUnitDocuments.length; i++) myJUnitDocuments[i] = new PlainDocument();
    myProject = project;
  }

  public void setType(int type) {
    if (type == myType) return;
    if (type < 0 || type >= ourTestObjects.size()) type = CLASS;
    myType = type;
    fireTypeChanged(type);
  }

  private void fireTypeChanged(final int newType) {
    myListener.onTypeChanged(newType);
  }

  public void setListener(final JUnitConfigurable listener) { myListener = listener; }

  public Document getJUnitDocument(final int i) {
    return myJUnitDocuments[i];
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
    if (testObject != JUnitConfiguration.TEST_PACKAGE && testObject != JUnitConfiguration.TEST_PATTERN) {
      final PsiClass testClass = JUnitUtil.findPsiClass(className, module, myProject);
      data.METHOD_NAME = getJUnitTextValue(METHOD);
      if (testClass != null && testClass.isValid()) {
        data.setMainClass(testClass);
      }
      else {
        data.MAIN_CLASS_NAME = className;
      }
    }
    else {
      if (testObject == JUnitConfiguration.TEST_PACKAGE) {
        data.PACKAGE_NAME = getJUnitTextValue(ALL_IN_PACKAGE);
      }
      else {
        final LinkedHashSet<String> set = new LinkedHashSet<String>();
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

  private static String getDocumentText(final int index, final Document[] documents) {
    final Document document = documents[index];
    try {
      return document.getText(0, document.getLength());
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public void reset(final JUnitConfiguration configuration) {
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    setTestType(data.TEST_OBJECT);
    setJUnitTextValue(ALL_IN_PACKAGE, data.getPackageName());
    setJUnitTextValue(CLASS, data.getMainClassName());
    setJUnitTextValue(METHOD, data.getMethodName());
    setJUnitTextValue(PATTERN, data.getPatternPresentation());
  }

  private void setJUnitTextValue(final int index, final String text) {
    setDocumentText(index, text, myJUnitDocuments);
  }

  private static void setDocumentText(final int index, final String text, final Document[] documents) {
    final Document document = documents[index];
    try {
      document.remove(0, document.getLength());
      document.insertString(0, text, null);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  private void setTestType(final String testObject) {
    setType(ourTestObjects.indexOf(testObject));
  }
}

