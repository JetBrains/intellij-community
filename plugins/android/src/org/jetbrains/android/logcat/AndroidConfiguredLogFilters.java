/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.logcat;

import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidConfiguredLogFilters",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class AndroidConfiguredLogFilters implements PersistentStateComponent<AndroidConfiguredLogFilters> {
  private List<MyFilterEntry> myFilterEntries = new ArrayList<MyFilterEntry>();

  @Override
  public AndroidConfiguredLogFilters getState() {
    return this;
  }

  @Override
  public void loadState(AndroidConfiguredLogFilters state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static AndroidConfiguredLogFilters getInstance(final Project project) {
    return ServiceManager.getService(project, AndroidConfiguredLogFilters.class);
  }

  @Tag("filters")
  @AbstractCollection(surroundWithTag = false)
  public List<MyFilterEntry> getFilterEntries() {
    return myFilterEntries;
  }
  
  @Nullable
  public MyFilterEntry findFilterEntryByName(@NotNull String name) {
    for (MyFilterEntry entry : myFilterEntries) {
      if (name.equals(entry.getName())) {
        return entry;
      }
    }
    return null;
  }

  public void setFilterEntries(List<MyFilterEntry> filterEntries) {
    myFilterEntries = filterEntries;
  }

  @JdkConstants.PatternFlags
  static int getPatternCompileFlags(@NotNull String regex) {
    for (char c : regex.toCharArray()) {
      if (Character.isUpperCase(c)) {
        return 0;
      }
    }

    return Pattern.CASE_INSENSITIVE;
  }

  @Tag("filter")
  public static class MyFilterEntry {
    private String myName;
    private String myLogMessagePattern;
    private String myLogLevel;
    private String myLogTagPattern;
    private String myPid;

    public String getName() {
      return myName;
    }

    public String getLogMessagePattern() {
      return myLogMessagePattern;
    }

    public String getLogLevel() {
      return myLogLevel;
    }

    public String getLogTagPattern() {
      return myLogTagPattern;
    }

    public String getPid() {
      return myPid;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setLogMessagePattern(String logMessagePattern) {
      myLogMessagePattern = logMessagePattern;
    }

    public void setLogLevel(String logLevel) {
      myLogLevel = logLevel;
    }

    public void setLogTagPattern(String logTagPattern) {
      myLogTagPattern = logTagPattern;
    }

    public void setPid(String pid) {
      myPid = pid;
    }
  }
}
