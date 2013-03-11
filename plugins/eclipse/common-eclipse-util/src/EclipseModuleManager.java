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
package org.jetbrains.idea.eclipse;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User: anna
 * Date: 10/29/12
 */
public interface EclipseModuleManager {
  void setInvalidJdk(String invalidJdk);

  @Nullable
  String getInvalidJdk();

  void registerCon(String name);

  String[] getUsedCons();

  void registerEclipseVariablePath(String path, String var);

  void registerEclipseSrcVariablePath(String path, String var);

  void registerEclipseLinkedSrcVarPath(String path, String var);

  @Nullable
  String getEclipseLinkedSrcVariablePath(String path);

  void registerEclipseLinkedVarPath(String path, String var);

  @Nullable
  String getEclipseLinkedVarPath(String path);

  @Nullable
  String getEclipseVariablePath(String path);

  @Nullable
  String getEclipseSrcVariablePath(String path);

  void registerUnknownCons(String con);

  @Nullable
  Set<String> getUnknownCons();

  boolean isForceConfigureJDK();

  void setForceConfigureJDK();

  void registerEclipseLibUrl(String url);

  boolean isEclipseLibUrl(String url);

  void setExpectedModuleSourcePlace(int expectedModuleSourcePlace);

  boolean isExpectedModuleSourcePlace(int expectedPlace);

  void registerSrcPlace(String srcUrl, int placeIdx);

  @Nullable
  Integer getSrcPlace(String srcUtl);
  
  EclipseModuleManager EMPTY = new EclipseModuleManager() {
    @Override
    public void setInvalidJdk(String invalidJdk) {}

    @Nullable
    @Override
    public String getInvalidJdk() {
      return null;
    }

    @Override
    public void registerCon(String name) {}

    @Override
    public String[] getUsedCons() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public void registerEclipseVariablePath(String path, String var) {}

    @Override
    public void registerEclipseSrcVariablePath(String path, String var) {}

    @Override
    public void registerEclipseLinkedSrcVarPath(String path, String var) {}

    @Nullable
    @Override
    public String getEclipseLinkedSrcVariablePath(String path) {
      return null;
    }

    @Override
    public void registerEclipseLinkedVarPath(String path, String var) {
    }

    @Nullable
    @Override
    public String getEclipseLinkedVarPath(String path) {
      return null;
    }

    @Nullable
    @Override
    public String getEclipseVariablePath(String path) {
      return null;
    }

    @Nullable
    @Override
    public String getEclipseSrcVariablePath(String path) {
      return null;
    }

    @Override
    public void registerUnknownCons(String con) {}

    @Nullable
    @Override
    public Set<String> getUnknownCons() {
      return null;
    }

    @Override
    public boolean isForceConfigureJDK() {
      return false;
    }

    @Override
    public void setForceConfigureJDK() {}

    @Override
    public void registerEclipseLibUrl(String url) {}

    @Override
    public boolean isEclipseLibUrl(String url) {
      return false;
    }

    @Override
    public void setExpectedModuleSourcePlace(int expectedModuleSourcePlace) {
    }

    @Override
    public boolean isExpectedModuleSourcePlace(int expectedPlace) {
      return false;
    }

    @Override
    public void registerSrcPlace(String srcUrl, int placeIdx) {
    }

    @Nullable
    @Override
    public Integer getSrcPlace(String srcUtl) {
      return null;
    }
  }; 
}
