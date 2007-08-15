package com.intellij.execution.junit;

public abstract class JUnitConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  public static final RuntimeConfigurationProducer[] PROTOTYPES = new RuntimeConfigurationProducer[]{
        new AllInPackageConfigurationProducer(),
        new TestMethodConfigurationProducer(),
        new TestClassConfigurationProducer()};

  public JUnitConfigurationProducer() {
    super(JUnitConfigurationType.getInstance());
  }

  
  public int compareTo(final Object o) {
    return PREFERED;
  }
}
