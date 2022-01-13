// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.codeInsight.hierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static junit.framework.TestCase.assertEquals;

public final class HierarchyViewTestFixture {
  private static final String NODE_ELEMENT_NAME = "node";
  private static final String ANY_NODES_ELEMENT_NAME = "any";
  private static final String TEXT_ATTR_NAME = "text";
  private static final String BASE_ATTR_NAME = "base";

  /**
   * Check the tree structure against the expected.
   * @param treeStructure tree structure to check
   * @param expectedStructure an expected structure in XML format.
   *                          If you load expected XML structure from the file,
   *                          better to use {@link #doHierarchyTest(HierarchyTreeStructure, File)} instead.
   */
  public static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                     @NotNull String expectedStructure) {
    doHierarchyTest(treeStructure, expectedStructure, null, null);
  }

  /**
   * Check the tree structure against the expected.
   * @param treeStructure tree structure to check
   * @param expectedFile an XML file containing expected structure
   * @throws IOException if expectedFile reading failed
   * @throws FileComparisonFailure if content doesn't match
   */
  public static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                     @NotNull File expectedFile) throws IOException {
    doHierarchyTest(treeStructure, null, expectedFile);
  }
  public static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                     @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                                     @NotNull File expectedFile) throws IOException {
    doHierarchyTest(treeStructure, FileUtil.loadFile(expectedFile), comparator, expectedFile);
  }

  private static void doHierarchyTest(@NotNull HierarchyTreeStructure treeStructure,
                                      @NotNull String expectedStructure,
                                      @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                                      @Nullable File expectedFile) {
    Element element;
    try {
      element = JDOMUtil.load(expectedStructure);
    }
    catch (Throwable e) {
      String actual = dump(treeStructure, null, comparator, 0);
      if (!expectedStructure.equals(actual)) {
        throw new FileComparisonFailure("XML structure comparison for your convenience, actual failure details BELOW",
                                        expectedStructure, actual,
                                        expectedFile == null ? null : expectedFile.getAbsolutePath());
      }
      throw new RuntimeException(e);
    }
    checkHierarchyTreeStructure(treeStructure, element, comparator);
  }

  @NotNull
  public static String dump(@NotNull HierarchyTreeStructure treeStructure,
                            @Nullable HierarchyNodeDescriptor descriptor,
                            @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                            int level) {
    StringBuilder s = new StringBuilder();
    dump(treeStructure, descriptor, comparator,level, s);
    return s.toString();
  }

  private static void dump(@NotNull HierarchyTreeStructure treeStructure,
                           @Nullable HierarchyNodeDescriptor descriptor,
                           @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                           int level,
                           @NotNull StringBuilder b) {
    if (level > 10) {
      b.append("  ".repeat(level));
      b.append("<Probably infinite part skipped>\n");
      return;
    }
    if (descriptor == null) descriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    b.append("  ".repeat(level));
    descriptor.update();
    b.append("<node text=\"").append(descriptor.getHighlightedText().getText()).append("\"")
      .append(treeStructure.getBaseDescriptor() == descriptor ? " base=\"true\"" : "");

    Object[] children = getSortedChildren(treeStructure, descriptor, comparator);
    if (children.length > 0) {
      b.append(">\n");
      for (Object o : children) {
        HierarchyNodeDescriptor d = (HierarchyNodeDescriptor)o;
        dump(treeStructure, d, comparator, level + 1, b);
      }
      b.append("  ".repeat(level));
      b.append("</node>\n");
    }
    else {
      b.append("/>\n");
    }
  }

  @NotNull
  private static Object @NotNull [] getSortedChildren(@NotNull HierarchyTreeStructure treeStructure,
                                                      @NotNull HierarchyNodeDescriptor descriptor,
                                                      @Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    Object[] children = treeStructure.getChildElements(descriptor);
    if (comparator == null) comparator = Comparator.comparingInt(NodeDescriptor::getIndex);
    Arrays.sort(children, (Comparator)comparator);
    return children;
  }

  private static void checkHierarchyTreeStructure(@NotNull HierarchyTreeStructure treeStructure,
                                                  @Nullable Element rootElement,
                                                  @Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    HierarchyNodeDescriptor rootNodeDescriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    rootNodeDescriptor.update();
    if (rootElement == null || !NODE_ELEMENT_NAME.equals(rootElement.getName())) {
      throw new IllegalArgumentException("Incorrect root element in verification resource");
    }
    checkNodeDescriptorRecursively(treeStructure, rootNodeDescriptor, rootElement, comparator);
  }

  private static void checkNodeDescriptorRecursively(@NotNull HierarchyTreeStructure treeStructure,
                                                     @NotNull HierarchyNodeDescriptor descriptor,
                                                     @NotNull Element expectedElement,
                                                     @Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    checkBaseNode(treeStructure, descriptor, expectedElement);
    checkContent(descriptor, expectedElement);
    checkChildren(treeStructure, descriptor, expectedElement, comparator);
  }

  private static void checkBaseNode(@NotNull HierarchyTreeStructure treeStructure,
                                    @NotNull HierarchyNodeDescriptor descriptor,
                                    @NotNull Element expectedElement) {
    String baseAttrValue = expectedElement.getAttributeValue(BASE_ATTR_NAME);
    HierarchyNodeDescriptor baseDescriptor = treeStructure.getBaseDescriptor();
    boolean mustBeBase = "true".equalsIgnoreCase(baseAttrValue);
    assertEquals("Incorrect base node", mustBeBase, baseDescriptor == descriptor);
  }

  private static void checkContent(@NotNull HierarchyNodeDescriptor descriptor, @NotNull Element expectedElement) {
    assertEquals("parent: " + descriptor.getParentDescriptor(), expectedElement.getAttributeValue(TEXT_ATTR_NAME),
                 descriptor.getHighlightedText().getText());
  }

  private static void checkChildren(@NotNull HierarchyTreeStructure treeStructure,
                                    @NotNull HierarchyNodeDescriptor descriptor,
                                    @NotNull Element element,
                                    @Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    if (element.getChild(ANY_NODES_ELEMENT_NAME) != null) {
      return;
    }

    Object[] children = getSortedChildren(treeStructure, descriptor, comparator);
    List<Element> expectedChildren = new ArrayList<>(element.getChildren(NODE_ELEMENT_NAME));

    StringBuilder messageBuilder = new StringBuilder("Actual children of [" + descriptor.getHighlightedText().getText() + "]:\n");
    for (Object child : children) {
      HierarchyNodeDescriptor nodeDescriptor = (HierarchyNodeDescriptor)child;
      nodeDescriptor.update();
      messageBuilder.append("    [").append(nodeDescriptor.getHighlightedText().getText()).append("]\n");
    }
    assertEquals(messageBuilder.toString(), expectedChildren.size(), children.length);

    Arrays.sort(children, Comparator.comparing(child -> ((HierarchyNodeDescriptor)child).getHighlightedText().getText()));

    expectedChildren.sort(Comparator.comparing(child -> child.getAttributeValue(TEXT_ATTR_NAME)));

    Iterator<Element> iterator = expectedChildren.iterator();
    for (Object child : children) {
      checkNodeDescriptorRecursively(treeStructure, (HierarchyNodeDescriptor)child, iterator.next(), comparator);
    }
  }
}
