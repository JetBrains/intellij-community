/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionEP;
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
import org.junit.Assert;

import java.io.CharArrayReader;
import java.io.File;
import java.io.StreamTokenizer;
import java.util.*;

public class InspectionTestUtil {
  private InspectionTestUtil() {
  }

  protected static void compareWithExpected(Document expectedDoc, Document doc, boolean checkRange) throws Exception {
    List<Element> expectedProblems = new ArrayList<>(expectedDoc.getRootElement().getChildren("problem"));
    List<Element> reportedProblems = new ArrayList<>(doc.getRootElement().getChildren("problem"));

    Element[] expectedArray = expectedProblems.toArray(new Element[expectedProblems.size()]);
    boolean failed = false;

expected:
    for (Element expectedProblem : expectedArray) {
      Element[] reportedArrayed = reportedProblems.toArray(new Element[reportedProblems.size()]);
      for (Element reportedProblem : reportedArrayed) {
        if (compareProblemWithExpected(reportedProblem, expectedProblem, checkRange)) {
          expectedProblems.remove(expectedProblem);
          reportedProblems.remove(reportedProblem);
          continue expected;
        }
      }

      Document missing = new Document(expectedProblem.clone());
      System.out.println("The following haven't been reported as expected: " + new String(JDOMUtil.printDocument(missing, "\n")));
      failed = true;
    }

    for (Element reportedProblem : reportedProblems) {
      Document extra = new Document(reportedProblem.clone());
      System.out.println("The following has been unexpectedly reported: " + new String(JDOMUtil.printDocument(extra, "\n")));
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
                                        String testDir) {
    final Element root = new Element("problems");
    final Document doc = new Document(root);
    InspectionToolPresentation presentation = context.getPresentation(toolWrapper);

    presentation.updateContent();  //e.g. dead code need check for reachables
    presentation.exportResults(root, x -> false, x -> false);

    File file = new File(testDir + "/expected.xml");
    try {
      compareWithExpected(JDOMUtil.loadDocument(file), doc, checkRange);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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
