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
  @Nullable private final String  myServiceDirectory;
  private final           boolean myUseWrapper;

  private long    myTtlInMs;
  private String  myJavaHome;
  private boolean myVerboseApi;

  public RemoteGradleProcessSettings(@Nullable String gradleHome, @Nullable String directory, boolean wrapper) {
    myGradleHome = gradleHome;
    myServiceDirectory = directory;
    myUseWrapper = wrapper;
    setVerboseApi(USE_VERBOSE_GRADLE_API_BY_DEFAULT);
  }

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  @Nullable
  public String getServiceDirectory() {
    return myServiceDirectory;
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
  public int hashCode() {
    int result = myGradleHome != null ? myGradleHome.hashCode() : 0;
    result = 31 * result + (myServiceDirectory != null ? myServiceDirectory.hashCode() : 0);
    result = 31 * result + (myUseWrapper ? 1 : 0);
    result = 31 * result + (int)(myTtlInMs ^ (myTtlInMs >>> 32));
    result = 31 * result + (myJavaHome != null ? myJavaHome.hashCode() : 0);
    result = 31 * result + (myVerboseApi ? 1 : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteGradleProcessSettings settings = (RemoteGradleProcessSettings)o;

    if (myTtlInMs != settings.myTtlInMs) return false;
    if (myUseWrapper != settings.myUseWrapper) return false;
    if (myVerboseApi != settings.myVerboseApi) return false;
    if (myGradleHome != null ? !myGradleHome.equals(settings.myGradleHome) : settings.myGradleHome != null) return false;
    if (myJavaHome != null ? !myJavaHome.equals(settings.myJavaHome) : settings.myJavaHome != null) return false;
    if (myServiceDirectory != null ? !myServiceDirectory.equals(settings.myServiceDirectory) : settings.myServiceDirectory != null) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome + ", use wrapper: " + myUseWrapper;
  }
}
