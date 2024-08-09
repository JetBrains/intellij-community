// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
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

public final class InspectionTestUtil {
  private InspectionTestUtil() {
  }

  public static void compareWithExpected(Element expectedDoc, Element doc, boolean checkRange) throws Exception {
    List<Element> expectedProblems = new ArrayList<>(expectedDoc.getChildren("problem"));
    List<Element> reportedProblems = new ArrayList<>(doc.getChildren("problem"));

    for (Element problem1 : reportedProblems) {
      for (Element problem2 : reportedProblems) {
        if (problem1 != problem2 && compareProblemWithExpected(problem1, problem2, checkRange)) {
          Assert.fail("Duplicated problems reported: " + JDOMUtil.writeDocument(new Document(problem1)));
        }
      }
    }

    Element[] expectedArray = expectedProblems.toArray(new Element[0]);

    List<String> problems = new ArrayList<>();

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
      problems.add("The following haven't been reported as expected: " + JDOMUtil.writeDocument(missing));
    }

    for (Element reportedProblem : reportedProblems) {
      Document extra = new Document(reportedProblem.clone());
      problems.add("The following has been unexpectedly reported: " + JDOMUtil.writeDocument(extra));
    }

    if (!problems.isEmpty()) {
      Assert.fail(String.join("\n", problems) +
                  "\n where all reported are: " + JDOMUtil.writeElement(doc) +
                  "\n all expected are: " + JDOMUtil.writeElement(expectedDoc)
      );
    }
  }

  private static boolean compareProblemWithExpected(Element reportedProblem, Element expectedProblem, boolean checkRange) throws Exception {
    if (!compareFiles(reportedProblem, expectedProblem)) return false;
    if (!compareLines(reportedProblem, expectedProblem)) return false;
    if (!compareDescriptions(reportedProblem, expectedProblem)) return false;
    return !checkRange || compareTextRange(reportedProblem, expectedProblem);
  }

  private static boolean compareTextRange(final Element reportedProblem, final Element expectedProblem) {
    Element reportedTextRange = reportedProblem.getChild("entry_point");
    if (reportedTextRange == null) return false;
    Element expectedTextRange = expectedProblem.getChild("entry_point");
    return Objects.equals(reportedTextRange.getAttributeValue("TYPE"), expectedTextRange.getAttributeValue("TYPE")) &&
           Objects.equals(reportedTextRange.getAttributeValue("FQNAME"), expectedTextRange.getAttributeValue("FQNAME"));
  }

  private static boolean compareDescriptions(Element reportedProblem, Element expectedProblem) throws Exception {
    String expectedDescription = expectedProblem.getChildText("description");
    String reportedDescription = reportedProblem.getChildText("description");
    if (expectedDescription.equals(reportedDescription)) return true;

    StreamTokenizer tokenizer = new StreamTokenizer(new CharArrayReader(expectedDescription.toCharArray()));
    tokenizer.quoteChar('\'');
    tokenizer.ordinaryChar('/');

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

  private static boolean compareLines(Element reportedProblem, Element expectedProblem) {
    return Objects.equals(reportedProblem.getChildText("line"), expectedProblem.getChildText("line"));
  }

  private static boolean compareFiles(Element reportedProblem, Element expectedProblem) {
    String reportedFileName = reportedProblem.getChildText("file");
    if (reportedFileName == null) {
      return true;
    }
    File reportedFile = new File(reportedFileName);

    return Objects.equals(reportedFile.getName(), expectedProblem.getChildText("file"));
  }

  public static void compareToolResults(@NotNull GlobalInspectionContextImpl context,
                                        @NotNull InspectionToolWrapper<?,?> toolWrapper,
                                        boolean checkRange,
                                        @NotNull String testDir) {
    compareToolResults(context, checkRange, testDir, Collections.singletonList(toolWrapper));
  }

  static void compareToolResults(@NotNull GlobalInspectionContextImpl context,
                                 boolean checkRange,
                                 @NotNull String testDir,
                                 @NotNull Collection<? extends InspectionToolWrapper<?,?>> toolWrappers) {
    final Element root = new Element(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);

    for (InspectionToolWrapper<?,?> toolWrapper : toolWrappers) {
      InspectionToolPresentation presentation = context.getPresentation(toolWrapper);
      presentation.updateContent();  //e.g. dead code need check for reachables
      presentation.exportResults(p -> root.addContent(p), x -> false, x -> false);
    }

    try {
      File file = new File(testDir + "/expected.xml");
      compareWithExpected(JDOMUtil.load(file), root, checkRange);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  public static void runTool(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                             @NotNull final AnalysisScope scope,
                             @NotNull final GlobalInspectionContextForTests globalContext) {
    IndexingTestUtil.waitUntilIndexesAreReady(scope.getProject());
    final String shortName = toolWrapper.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      Computable.PredefinedValueComputable<String> displayName = new Computable.PredefinedValueComputable<>(toolWrapper.getDisplayName());
      HighlightDisplayKey.register(shortName, displayName, toolWrapper.getID());
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

  public static <T extends InspectionProfileEntry> T instantiateTool(Class<? extends T> inspection) {
    //noinspection unchecked
    return (T)instantiateTools(Collections.singleton(inspection)).get(0);
  }

  @NotNull
  public static List<InspectionProfileEntry> instantiateTools(Set<String> classNames) {
    List<InspectionProfileEntry> tools = JBIterable.of(LocalInspectionEP.LOCAL_INSPECTION, InspectionEP.GLOBAL_INSPECTION)
      .flatten(o -> o.getExtensionList())
      .filter(o -> classNames.contains(o.implementationClass))
      .transform(InspectionEP::instantiateTool)
      .toList();
    if (tools.size() != classNames.size()) {
      Set<String> missing = new TreeSet<>(classNames);
      missing.removeAll(JBIterable.from(tools).transform(o -> o.getClass().getName()).toSet());
      throw new RuntimeException("Unregistered inspections requested: " + missing);
    }
    return tools;
  }
}
