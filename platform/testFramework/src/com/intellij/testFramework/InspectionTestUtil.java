// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.io.CharArrayReader;
import java.io.File;
import java.io.StreamTokenizer;
import java.util.*;

public class InspectionTestUtil {
  private InspectionTestUtil() {
  }

  public static void compareWithExpected(Document expectedDoc, Document doc, boolean checkRange) throws Exception {
    List<Element> expectedProblems = new ArrayList<>(expectedDoc.getRootElement().getChildren("problem"));
    List<Element> reportedProblems = new ArrayList<>(doc.getRootElement().getChildren("problem"));

    Element[] expectedArray = expectedProblems.toArray(new Element[0]);
    boolean failed = false;

    expected:
    for (Element expectedProblem : expectedArray) {
      Element[] reportedArrayed = reportedProblems.toArray(new Element[0]);
      for (Element reportedProblem : reportedArrayed) {
        if (compareProblemWithExpected(reportedProblem, expectedProblem, checkRange)) {
          expectedProblems.remove(expectedProblem);
          reportedProblems.remove(reportedProblem);
          continue expected;
        }
      }

      Document missing = new Document(expectedProblem.clone());
      System.out.println("The following haven't been reported as expected: " + JDOMUtil.writeDocument(missing, "\n"));
      failed = true;
    }

    for (Element reportedProblem : reportedProblems) {
      Document extra = new Document(reportedProblem.clone());
      System.out.println("The following has been unexpectedly reported: " + JDOMUtil.writeDocument(extra, "\n"));
      failed = true;
    }

    Assert.assertFalse(failed);
  }

  static boolean compareProblemWithExpected(Element reportedProblem, Element expectedProblem, boolean checkRange) throws Exception {
    if (!compareFiles(reportedProblem, expectedProblem)) return false;
    if (!compareLines(reportedProblem, expectedProblem)) return false;
    if (!compareDescriptions(reportedProblem, expectedProblem)) return false;
    if (checkRange && !compareTextRange(reportedProblem, expectedProblem)) return false;
    return true;
  }

  static boolean compareTextRange(final Element reportedProblem, final Element expectedProblem) {
    Element reportedTextRange = reportedProblem.getChild("entry_point");
    if (reportedTextRange == null) return false;
    Element expectedTextRange = expectedProblem.getChild("entry_point");
    return Comparing.equal(reportedTextRange.getAttributeValue("TYPE"), expectedTextRange.getAttributeValue("TYPE")) &&
           Comparing.equal(reportedTextRange.getAttributeValue("FQNAME"), expectedTextRange.getAttributeValue("FQNAME"));
  }

  static boolean compareDescriptions(Element reportedProblem, Element expectedProblem) throws Exception {
    String expectedDescription = expectedProblem.getChildText("description");
    String reportedDescription = reportedProblem.getChildText("description");
    if (expectedDescription.equals(reportedDescription)) return true;

    StreamTokenizer tokenizer = new StreamTokenizer(new CharArrayReader(expectedDescription.toCharArray()));
    tokenizer.quoteChar('\'');

    int idx = 0;
    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      String word;
      if (tokenizer.sval != null) {
        word = tokenizer.sval;
      } else if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
        word = Double.toString(tokenizer.nval);
      }
      else {
        continue;
      }

      idx = reportedDescription.indexOf(word, idx);
      if (idx == -1) return false;
      idx += word.length();
    }

    return true;
  }

  static boolean compareLines(Element reportedProblem, Element expectedProblem) {
    return Comparing.equal(reportedProblem.getChildText("line"), expectedProblem.getChildText("line"));
  }

  static boolean compareFiles(Element reportedProblem, Element expectedProblem) {
    String reportedFileName = reportedProblem.getChildText("file");
    if (reportedFileName == null) {
      return true;
    }
    File reportedFile = new File(reportedFileName);

    return Comparing.equal(reportedFile.getName(), expectedProblem.getChildText("file"));
  }

  public static void compareToolResults(@NotNull GlobalInspectionContextImpl context,
                                        @NotNull InspectionToolWrapper toolWrapper,
                                        boolean checkRange,
                                        @NotNull String testDir) {
    compareToolResults(context, checkRange, testDir, Collections.singletonList(toolWrapper));
  }

  static void compareToolResults(@NotNull GlobalInspectionContextImpl context,
                                 boolean checkRange,
                                 @NotNull String testDir,
                                 @NotNull Collection<? extends InspectionToolWrapper> toolWrappers) {
    final Element root = new Element(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);

    for (InspectionToolWrapper toolWrapper : toolWrappers) {
      InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
      presentation.updateContent();  //e.g. dead code need check for reachables
      presentation.exportResults(p -> root.addContent(p), x -> false, x -> false);
    }

    try {
      File file = new File(testDir + "/expected.xml");
      compareWithExpected(JDOMUtil.loadDocument(file), new Document(root), checkRange);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  public static void runTool(@NotNull InspectionToolWrapper toolWrapper,
                             @NotNull final AnalysisScope scope,
                             @NotNull final GlobalInspectionContextForTests globalContext) {
    final String shortName = toolWrapper.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      HighlightDisplayKey.register(shortName);
    }

    globalContext.doInspections(scope);
    do {
      UIUtil.dispatchAllInvocationEvents();
    }
    while (!globalContext.isFinished());
  }

  @NotNull
  public static <T extends InspectionProfileEntry> List<InspectionProfileEntry> instantiateTools(@NotNull Collection<Class<? extends T>> inspections) {
    Set<String> classNames = JBIterable.from(inspections).transform(Class::getName).toSet();
    return instantiateTools(classNames);
  }

  @NotNull
  public static List<InspectionProfileEntry> instantiateTools(Set<String> classNames) {
    List<InspectionProfileEntry> tools = JBIterable.of(LocalInspectionEP.LOCAL_INSPECTION, InspectionEP.GLOBAL_INSPECTION)
      .flatten((o) -> Arrays.asList(o.getExtensions()))
      .filter((o) -> classNames.contains(o.implementationClass))
      .transform(InspectionEP::instantiateTool)
      .toList();
    if (tools.size() != classNames.size()) {
      Set<String> missing = ContainerUtil.newTreeSet(classNames);
      missing.removeAll(JBIterable.from(tools).transform((o) -> o.getClass().getName()).toSet());
      throw new RuntimeException("Unregistered inspections requested: " + missing);
    }
    return tools;
  }
}
