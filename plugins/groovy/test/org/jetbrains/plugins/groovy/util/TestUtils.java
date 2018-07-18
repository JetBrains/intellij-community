// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.util;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.CollectConsumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility class, that contains various methods for testing
 *
 * @author Ilya.Sergey
 */
public abstract class TestUtils {
  public static final String TEMP_FILE = "temp.groovy";
  public static final String GSP_TEMP_FILE = "temp.gsp";
  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";
  public static final String GROOVY_JAR = "groovy-all.jar";
  public static final String GROOVY_JAR_17 = "groovy-all-1.7.jar";
  public static final String GROOVY_JAR_18 = "groovy-1.8.0-beta-2.jar";
  public static final String GROOVY_JAR_21 = "groovy-all-2.1.3.jar";
  public static final String GROOVY_JAR_22 = "groovy-all-2.2.0-beta-1.jar";
  public static final String GROOVY_JAR_23 = "groovy-all-2.3.0.jar";
  public static final String GROOVY_JAR_30 = "groovy-3.0.0-alpha-2.jar";

  public static String getMockJdkHome() {
    return getAbsoluteTestDataPath() + "/mockJDK";
  }

  public static String getMockGroovyLibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib";
  }

  public static String getMockGroovy1_6LibraryName() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib1.6/lib/groovy-all-1.6.jar";
  }

  public static String getMockGroovy1_7LibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib1.7";
  }

  public static String getMockGroovy1_7LibraryName() {
    return getMockGroovy1_7LibraryHome()+"/groovy-all-1.7.3.jar";
  }

  public static String getMockGroovy1_8LibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib1.8";
  }

  public static String getMockGroovy2_1LibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib2.1";
  }

  private static String getMockGroovy2_2LibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib2.2";
  }

  private static String getMockGroovy2_3LibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib2.3";
  }

  private static String getMockGroovy3_0LibraryHome() {
    return getAbsoluteTestDataPath() + "/mockGroovyLib3.0";
  }

  public static String getMockGroovy1_8LibraryName() {
    return getMockGroovy1_8LibraryHome() + "/" + GROOVY_JAR_18;
  }

  public static String getMockGroovy2_1LibraryName() {
    return getMockGroovy2_1LibraryHome() + "/" + GROOVY_JAR_21;
  }

  public static String getMockGroovy2_2LibraryName() {
    return getMockGroovy2_2LibraryHome() + "/" + GROOVY_JAR_22;
  }

  public static String getMockGroovy2_3LibraryName() {
    return getMockGroovy2_3LibraryHome() + "/" + GROOVY_JAR_23;
  }

  public static String getMockGroovy3_0LibraryName() {
    return getMockGroovy3_0LibraryHome() + "/" + GROOVY_JAR_30;
  }

  public static PsiFile createPseudoPhysicalGroovyFile(final Project project, final String text) throws IncorrectOperationException {
    return createPseudoPhysicalFile(project, TEMP_FILE, text);
  }


  public static PsiFile createPseudoPhysicalFile(final Project project, final String fileName, final String text) throws IncorrectOperationException {
    return PsiFileFactory.getInstance(project).createFileFromText(
        fileName,
        FileTypeManager.getInstance().getFileTypeByFileName(fileName),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }

  public static String getAbsoluteTestDataPath() {
    return FileUtil.toSystemIndependentName(PluginPathManager.getPluginHomePath("groovy")) + "/testdata/";
  }

  public static String getTestDataPath() {
    return FileUtil.toSystemIndependentName(PluginPathManager.getPluginHomePathRelative("groovy")) + "/testdata/";
  }

  public static String removeBeginMarker(String text) {
    int index = text.indexOf(BEGIN_MARKER);
    return text.substring(0, index) + text.substring(index + BEGIN_MARKER.length());
  }

  public static String removeEndMarker(String text) {
    int index = text.indexOf(END_MARKER);
    return text.substring(0, index) + text.substring(index + END_MARKER.length());
  }

  public static List<String> readInput(String filePath) {
    String content;
    try {
      content = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(filePath)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<>();

    int separatorIndex;
    content = StringUtil.replace(content, "\r", ""); // for MACs

    // Adding input  before -----
    while ((separatorIndex = content.indexOf("-----")) >= 0) {
      input.add(content.substring(0, separatorIndex - 1));
      content = content.substring(separatorIndex);
      while (StringUtil.startsWithChar(content, '-')) {
        content = content.substring(1);
      }
      if (StringUtil.startsWithChar(content, '\n')) {
        content = content.substring(1);
      }
    }
    input.add(content);

    Assert.assertTrue("No data found in source file", input.size() > 0);
    Assert.assertNotNull("Test output points to null", input.size() > 1);

    return input;
  }

  public static void checkCompletionContains(JavaCodeInsightTestFixture fixture, PsiFile file, String ... expectedVariants) {
    fixture.configureFromExistingVirtualFile(file.getVirtualFile());
    checkCompletionContains(fixture, expectedVariants);
  }

  public static void checkCompletionContains(JavaCodeInsightTestFixture fixture, String ... expectedVariants) {
    LookupElement[] lookupElements = fixture.completeBasic();

    Assert.assertNotNull(lookupElements);

    Set<String> missedVariants = ContainerUtil.newHashSet(expectedVariants);

    for (LookupElement lookupElement : lookupElements) {
      String lookupString = lookupElement.getLookupString();
      missedVariants.remove(lookupString);

      Object object = lookupElement.getObject();
      if (object instanceof ResolveResult) {
        object = ((ResolveResult)object).getElement();
      }

      if (object instanceof PsiMethod) {
        missedVariants.remove(lookupString + "()");
      }
      else if (object instanceof PsiVariable) {
        missedVariants.remove('@' + lookupString);
      }
      else if (object instanceof NamedArgumentDescriptor) {
        missedVariants.remove(lookupString + ':');
      }
    }

    if (missedVariants.size() > 0) {
      Assert.assertTrue("Some completion variants are missed " + missedVariants, false);
    }
  }

  public static void checkCompletionType(JavaCodeInsightTestFixture fixture, String lookupString, String expectedTypeCanonicalText) {
    LookupElement[] lookupElements = fixture.completeBasic();
    PsiType type = null;

    for (LookupElement lookupElement : lookupElements) {
      if (lookupElement.getLookupString().equals(lookupString)) {
        PsiElement element = lookupElement.getPsiElement();
        if (element instanceof PsiField) {
          type = ((PsiField)element).getType();
          break;
        }
        if (element instanceof PsiMethod) {
          type = ((PsiMethod)element).getReturnType();
          break;
        }
      }
    }

    if (type == null) {
      Assert.fail("No field or method called '" + lookupString + "' found in completion lookup elements");
    }

    Assert.assertEquals(expectedTypeCanonicalText, type.getCanonicalText());
  }

  public static void checkResolve(PsiFile file, final String ... expectedUnresolved) {
    final List<String> actualUnresolved = new ArrayList<>();

    final StringBuilder sb = new StringBuilder();
    final String text = file.getText();
    final Ref<Integer> lastUnresolvedRef = Ref.create(0);

    file.acceptChildren(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof GrReferenceExpression) {
          GrReferenceExpression psiReference = (GrReferenceExpression)element;

          GrExpression qualifier = psiReference.getQualifierExpression();
          if (qualifier instanceof GrReferenceExpression) {
            if (((GrReferenceExpression)qualifier).resolve() == null) {
              super.visitElement(element);
              return;
            }
          }

          if (psiReference.resolve() == null) {
            CollectConsumer<PomTarget> consumer = new CollectConsumer<>();

            for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
              searcher.findDeclarationsAt(psiReference, 0, consumer);
              if (consumer.getResult().size() > 0) break;
            }

            if (consumer.getResult().isEmpty()) {
              PsiElement nameElement = psiReference.getReferenceNameElement();
              assert nameElement != null;

              String name = nameElement.getText();

              assert name.equals(psiReference.getReferenceName());

              int last = lastUnresolvedRef.get();
              sb.append(text, last, nameElement.getTextOffset());
              sb.append('!').append(name).append('!');
              lastUnresolvedRef.set(nameElement.getTextOffset() + nameElement.getTextLength());

              actualUnresolved.add(name);
              return;
            }
          }
        }

        super.visitElement(element);
      }

      @Override
      public void visitFile(PsiFile file) {
      }
    });

    sb.append("\n\n");

    Assert.assertEquals(sb.toString(), Arrays.asList(expectedUnresolved), actualUnresolved);
  }

  public static PsiMethod[] getMethods(PsiClass aClass) {
    //workaround for IDEA-148973: Groovy static compilation fails to compile calls of overriding methods with covariant type in interfaces
    return aClass.getMethods();
  }

  public static void disableAstLoading(@NotNull Project project, @NotNull Disposable parent) {
    PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, parent);
  }
}
