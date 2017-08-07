package org.jetbrains.plugins.gradle.tooling.internal.backRefCollector;

// used in bootclasspath to have only one instance of index per process
public class ReferenceIndexHolder {

  public static volatile boolean ourInProcess;
  public static volatile Object ourIndexWriter;

}
