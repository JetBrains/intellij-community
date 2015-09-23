package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MismatchedCollectionQueryUpdateInspectionTest extends LightInspectionTestCase {

  public void testMismatchedCollectionQueryUpdate() throws Exception {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public class HashSet<E> implements Set<E> {" +
      "  public HashSet() {}" +
      "  public HashSet(Collection<? extends E> collection) {}" +
      "}",
      "package java.util.concurrent;" +
      "public interface BlockingDeque<E> {" +
      "  E takeFirst() throws InterruptedException;" +
      "  void putLast(E e) throws InterruptedException;" +
      "}",
      "package java.util.concurrent;" +
      "public class LinkedBlockingDeque<E> implements BlockingDeque {}",
      "package java.lang;" +
      "public class InterruptedException extends Exception {}",
      "package java.util.concurrent;" +
      "public interface BlockingQueue<E> {" +
      "  int drainTo(java.util.Collection<? super E> c);" +
      "}"
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MismatchedCollectionQueryUpdateInspection();
  }
}