// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SkipSlowTestLocally
public class SmartPsiElementPointersPerformanceTest extends LightPlatformCodeInsightTestCase {
  private SmartPointerManagerImpl getPointerManager() {
    return (SmartPointerManagerImpl)SmartPointerManager.getInstance(getProject());
  }

  @NotNull
  private <T extends PsiElement> SmartPointerEx<T> createPointer(T element) {
    return (SmartPointerEx<T>)getPointerManager().createSmartPsiElementPointer(element);
  }

  public void testLargeFileWithManyChangesPerformance() {
    String text = StringUtil.repeat("foo foo \n", 50000);
    PsiFile file = createFile("a.txt", text);
    final TextRange range = TextRange.from(10, 10);
    final SmartPsiFileRange pointer = getPointerManager().createSmartPsiFileRangePointer(file, range);

    final Document document = file.getViewProvider().getDocument();
    assertNotNull(document);

    WriteAction.run(() -> Benchmark.newBenchmark("smart pointer range update", () -> {
      for (int i = 0; i < 10000; i++) {
        document.insertString(i * 20 + 100, "x\n");
        assertFalse(PsiDocumentManager.getInstance(getProject()).isCommitted(document));
        assertEquals(range, pointer.getRange());
      }
    }).setup(() -> {
      document.setText(text);
      assertEquals(range, pointer.getRange());
    }).start());

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals(range, pointer.getRange());
  }

  public void testManyPsiChangesWithManySmartPointersPerformance() {
    String eachTag = "<a>\n" + StringUtil.repeat("   <a> </a>\n", 9) + "</a>\n";
    XmlFile file = (XmlFile)createFile("a.xml", "<root>\n" + StringUtil.repeat(eachTag, 500) + "</root>");
    List<XmlTag> tags = new ArrayList<>(PsiTreeUtil.findChildrenOfType(file.getDocument(), XmlTag.class));
    List<SmartPsiElementPointer<XmlTag>> pointers = ContainerUtil.map(tags, this::createPointer);
    ApplicationManager.getApplication().runWriteAction(() -> Benchmark.newBenchmark("smart pointer range update after PSI change", () -> {
      for (int i = 0; i < tags.size(); i++) {
        XmlTag tag = tags.get(i);
        SmartPsiElementPointer<XmlTag> pointer = pointers.get(i);
        assertEquals(tag.getName().length(), TextRange.create(pointer.getRange()).getLength());
        assertEquals(tag.getName().length(), TextRange.create(pointer.getPsiRange()).getLength());

        tag.setName("bar1" + (i % 10));
        assertEquals(tag.getName().length(), TextRange.create(pointer.getRange()).getLength());
        assertEquals(tag.getName().length(), TextRange.create(pointer.getPsiRange()).getLength());
      }
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    }).start());
  }

  public void testManySmartPointersCreationDeletionPerformance() {
    String text = StringUtil.repeatSymbol(' ', 100000);
    PsiFile file = createFile("a.txt", text);

    Benchmark.newBenchmark(getTestName(false), () -> {
      List<SmartPsiFileRange> pointers = new ArrayList<>();
      for (int i = 0; i < text.length() - 1; i++) {
        pointers.add(getPointerManager().createSmartPsiFileRangePointer(file, new TextRange(i, i + 1)));
      }
      Collections.shuffle(pointers);
      for (SmartPsiFileRange pointer : pointers) {
        getPointerManager().removePointer(pointer);
      }
    }).start();
  }
}
