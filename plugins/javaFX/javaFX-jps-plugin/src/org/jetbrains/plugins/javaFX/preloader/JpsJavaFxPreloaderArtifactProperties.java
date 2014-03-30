/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.preloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * User: anna
 * Date: 3/13/13
 */
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

  @NotNull
  @Override
  public JpsJavaFxPreloaderArtifactProperties createCopy() {
    return new JpsJavaFxPreloaderArtifactProperties(myState);
  }

  @Override
  public void applyChanges(@NotNull JpsJavaFxPreloaderArtifactProperties modified) {
    copyState(modified.myState);
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
