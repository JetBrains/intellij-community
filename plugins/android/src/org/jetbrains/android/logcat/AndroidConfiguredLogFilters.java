package org.jetbrains.android.logcat;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
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
    @Storage(file = "$WORKSPACE_FILE$")
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
