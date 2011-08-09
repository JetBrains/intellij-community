package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;

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

  private static final long serialVersionUID = 1L;
  
  private final String myGradleHome;

  public RemoteGradleProcessSettings(@NotNull String gradleHome) {
    myGradleHome = gradleHome;
  }

  @NotNull
  public String getGradleHome() {
    return myGradleHome;
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome;
  }
}
