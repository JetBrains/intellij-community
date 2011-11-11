package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
class ConfiguredFilter {
  private final String myName;
  private final Pattern myMessagePattern;
  private final Pattern myTagPattern;
  private final String myPid;
  private final Log.LogLevel myLogLevel;

  public ConfiguredFilter(@NotNull String name,
                          @Nullable Pattern messagePattern,
                          @Nullable Pattern tagPattern,
                          @Nullable String pid,
                          @Nullable Log.LogLevel logLevel) {
    myName = name;
    myMessagePattern = messagePattern;
    myTagPattern = tagPattern;
    myPid = pid;
    myLogLevel = logLevel;
  }
  
  public boolean isApplicable(String message, String tag, String pid, Log.LogLevel logLevel) {
    if (myMessagePattern != null && (message == null || !myMessagePattern.matcher(message).find())) {
      return false;
    }

    if (myTagPattern != null && (tag == null || !myTagPattern.matcher(tag).find())) {
      return false;
    }

    if (myPid != null && myPid.length() > 0 && !myPid.equals(pid)) {
      return false;
    }

    if (myLogLevel != null && (logLevel == null || logLevel.getPriority() < myLogLevel.getPriority())) {
      return false;
    }
    
    return true;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
