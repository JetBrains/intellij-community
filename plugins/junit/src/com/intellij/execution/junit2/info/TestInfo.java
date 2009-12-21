/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.info;

import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.rt.execution.junit.segments.PoolOfTestTypes;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class TestInfo implements PsiLocator {
  public static final Map<String,Class> KNOWN_PSI_LOCATOR_CLASSES = new HashMap<String, Class>();
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
    final TestInfo info;
    try {
      info = (TestInfo)  infoClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    info.readFrom(reader);
    info.setTestCount(reader.readInt());
    return info;
  }

  private int myTestCount;

  public boolean shouldRun() {
    return false;
  }

  public int getTestsCount() {
    return myTestCount;
  }

  public void setTestCount(final int testCount) {
    myTestCount = testCount;
  }

  public abstract void readFrom(ObjectReader reader);

  public abstract String getComment();

  public abstract String getName();

}
