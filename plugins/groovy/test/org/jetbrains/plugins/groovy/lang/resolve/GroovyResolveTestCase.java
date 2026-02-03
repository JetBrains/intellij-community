// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;

public abstract class GroovyResolveTestCase extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    if (new File(myFixture.getTestDataPath() + "/" + getTestName(true)).exists()) {
      myFixture.copyDirectoryToProject(getTestName(true), "");
    }
  }

  protected <T extends PsiReference> T configureByFile(@NonNls String filePath, @Nullable String newFilePath, Class<T> refType) {
    String trimmedFilePath = StringUtil.trimStart(filePath, getTestName(true) + "/");
    VirtualFile vFile = myFixture.getTempDirFixture().getFile(filePath);
    if (vFile == null) {
      vFile = myFixture.getTempDirFixture().getFile(trimmedFilePath);
    }

    Assert.assertNotNull("file " + filePath + " not found", vFile);

    String fileText;
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }


    int offset = fileText.indexOf(MARKER);
    Assert.assertTrue("'ref' marker is not found", offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    if (newFilePath == null) {
      myFixture.configureByText(vFile.getName(), fileText);
    }
    else {
      myFixture.configureByText(newFilePath, fileText);
    }


    PsiReference ref = myFixture.getFile().findReferenceAt(offset);
    return UsefulTestCase.assertInstanceOf(ref, refType);
  }

  protected PsiReference configureByFile(@NonNls String filePath, @Nullable String newFilePath) {
    return configureByFile(filePath, newFilePath, PsiReference.class);
  }

  protected PsiReference configureByFile(@NonNls String filePath) {
    return configureByFile(filePath, null, PsiReference.class);
  }

  protected <T extends PsiReference> T configureByText(String fileName, String text, Class<T> refType) {
    myFixture.configureByText(fileName, text);
    final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    return UsefulTestCase.assertInstanceOf(ref, refType);
  }

  protected PsiReference configureByText(String fileName, String text) {
    return configureByText(fileName, text, PsiReference.class);
  }

  protected PsiReference configureByText(String text) {
    return configureByText("_a.groovy", text, PsiReference.class);
  }

  @Nullable
  protected <T extends PsiElement> PsiElement resolve(String fileName, Class<T> type) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    Assert.assertNotNull(ref);
    final PsiElement resolved = ref.resolve();
    if (type != null) UsefulTestCase.assertInstanceOf(resolved, type);
    return resolved;
  }

  @Nullable
  protected PsiElement resolve(String fileName) {
    return resolve(fileName, null);
  }

  @Nullable
  protected PsiElement resolve() {
    return resolve(getTestName(false) + ".groovy", null);
  }

  @Nullable
  protected <T extends PsiElement> T resolveByText(String text, Class<T> type) {
    final PsiReference ref = configureByText(text);
    Assert.assertNotNull(ref);
    final PsiElement resolved = ref.resolve();
    if (type == null) {
      Assert.assertNull(resolved);
      return null;
    }
    return UsefulTestCase.assertInstanceOf(resolved, type);
  }

  @Nullable
  protected PsiElement resolveByText(String text) {
    return resolveByText(text, PsiElement.class);
  }

  @Nullable
  protected GroovyResolveResult advancedResolve(String fileName) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    UsefulTestCase.assertInstanceOf(ref, GrReferenceExpression.class);
    return ((GrReferenceExpression)ref).advancedResolve();
  }

  @NonNls protected static final String MARKER = "<ref>";
}
