package com.intellij.execution.junit2.info;

import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.rt.execution.junit.segments.PoolOfTestTypes;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class TestInfoFactory {
  private static final Map<String,Class> KNOWN_PSI_LOCATOR_CLASSES = new HashMap<String, Class>();

  static {
    KNOWN_PSI_LOCATOR_CLASSES.put(PoolOfTestTypes.TEST_METHOD, TestCaseInfo.class);
    KNOWN_PSI_LOCATOR_CLASSES.put(PoolOfTestTypes.TEST_CLASS, TestClassInfo.class);
    KNOWN_PSI_LOCATOR_CLASSES.put(PoolOfTestTypes.ALL_IN_PACKAGE, AllInPackageInfo.class);
  }

  @NotNull
  public static TestInfo readInfoFrom(final ObjectReader reader) {
    final String testType = reader.readLimitedString();
    Class infoClass = KNOWN_PSI_LOCATOR_CLASSES.get(testType);
    if (infoClass == null)
      infoClass = DefaultTestInfo.class;
    final TestInfoImpl info;
    try {
      info = (TestInfoImpl)  infoClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    info.readPacketFrom(reader);
    info.setTestCount(reader.readInt());
    return info;
  }
}
