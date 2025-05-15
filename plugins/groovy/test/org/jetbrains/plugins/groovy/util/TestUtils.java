// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Utility class, that contains various methods for testing
 *
 * @author Ilya.Sergey
 */
public abstract class TestUtils {
  public static final String TEMP_FILE = "temp.groovy";
  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";
  public static final String GROOVY_JAR = "groovy-all.jar";

  @NotNull
  public static String getMockJdkHome() {
    return getAbsoluteTestDataPath() + "/mockJDK";
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

  /**
   * Reads input file which consists at least of two sections separated with "-----" line
   * @param filePath file to read
   * @return a list of sections read from the file
   * @throws RuntimeException if any IO problem occurs
   */
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
    Assert.assertTrue("Test output points to null", input.size() > 1);

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
      Assert.fail("Some completion variants are missed " + missedVariants);
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
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof GrReferenceExpression psiReference) {

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
              Assert.assertNotNull(nameElement);

              String name = nameElement.getText();

              Assert.assertEquals(name, psiReference.getReferenceName());

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
      public void visitFile(@NotNull PsiFile psiFile) {
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

  public static <T> void runAll(@NotNull Collection<? extends T> input,
                                @NotNull ThrowableConsumer<? super T, Throwable> action) {
    new RunAll(ContainerUtil.map(input, it -> () -> action.consume(it))).run();
  }

  public static <K, V> void runAll(@NotNull Map<? extends K, ? extends V> input,
                                   @NotNull ThrowablePairConsumer<? super K, ? super V, Throwable> action) {
    runAll(input.entrySet(), e -> action.consume(e.getKey(), e.getValue()));
  }
}
