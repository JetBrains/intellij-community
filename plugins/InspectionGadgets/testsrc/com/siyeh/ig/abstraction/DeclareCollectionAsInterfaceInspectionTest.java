package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class DeclareCollectionAsInterfaceInspectionTest extends LightInspectionTestCase {

  public void testDeclareCollectionAsInterface() { doTest(); }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DeclareCollectionAsInterfaceInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {" +
      "  public boolean add(E e) { return null; }" +
      "}"
    };
  }
}