package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * Encapsulates settings important for the process that works with gradle tooling api.
 * <p/>
 * The main idea is that we don't want to use the tooling api at intellij process (avoid heap/cpu pollution)
 * and run separate process instead. Current class encapsulates settings significant for it.
 * 
 * @author Denis Zhdanov
 * @since 8/9/11 12:12 PM
 */
public class RemoteGradleProcessSettings implements Serializable {

  private static final boolean USE_VERBOSE_GRADLE_API_BY_DEFAULT = Boolean.parseBoolean(System.getProperty("gradle.api.verbose"));

  private static final long serialVersionUID = 1L;

  @Nullable private final String  myGradleHome;
  private final          boolean myUseWrapper;

  private long    myTtlInMs;
  private String  myJavaHome;
  private boolean myVerboseApi;

  public RemoteGradleProcessSettings(@Nullable String gradleHome, boolean wrapper) {
    myGradleHome = gradleHome;
    myUseWrapper = wrapper;
    setVerboseApi(USE_VERBOSE_GRADLE_API_BY_DEFAULT);
  }

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  public boolean isUseWrapper() {
    return myUseWrapper;
  }

  /**
   * @return ttl in milliseconds for the remote process (positive value); non-positive value if undefined
   */
  public long getTtlInMs() {
    return myTtlInMs;
  }

  public void setTtlInMs(long ttlInMs) {
    myTtlInMs = ttlInMs;
  }

  @Nullable
  public String getJavaHome() {
    return myJavaHome;
  }

  public void setJavaHome(@Nullable String javaHome) {
    myJavaHome = javaHome;
  }

  public boolean isVerboseApi() {
    return myVerboseApi;
  }

  public void setVerboseApi(boolean verboseApi) {
    myVerboseApi = verboseApi;
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome;
  }
}
