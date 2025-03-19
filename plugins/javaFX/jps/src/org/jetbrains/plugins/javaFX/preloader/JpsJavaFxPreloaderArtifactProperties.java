// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.preloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

public class JpsJavaFxPreloaderArtifactProperties extends JpsElementBase<JpsJavaFxPreloaderArtifactProperties> {
  protected MyState myState = new MyState();

  public JpsJavaFxPreloaderArtifactProperties() {
  }

  public JpsJavaFxPreloaderArtifactProperties(MyState state) {
    copyState(state);
  }

  private void copyState(MyState state) {
    myState.setPreloaderClass(state.myPreloaderClass);
  }

  @Override
  public @NotNull JpsJavaFxPreloaderArtifactProperties createCopy() {
    return new JpsJavaFxPreloaderArtifactProperties(myState);
  }

  public String getPreloaderClass() {
    return myState.getPreloaderClass();
  }

  public static class MyState {
    private String myPreloaderClass;

    public String getPreloaderClass() {
      return myPreloaderClass;
    }

    public void setPreloaderClass(String preloaderClass) {
      myPreloaderClass = preloaderClass;
    }
  }
}
