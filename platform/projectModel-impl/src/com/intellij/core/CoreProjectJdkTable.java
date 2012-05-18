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
package com.intellij.core;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CoreProjectJdkTable extends ProjectJdkTable {
  @Override
  public Sdk findJdk(String name) {
    return null;
  }

  @Override
  public Sdk findJdk(String name, String type) {
    return null;
  }

  @Override
  public Sdk[] getAllJdks() {
    return new Sdk[0];
  }

  @Override
  public List<Sdk> getSdksOfType(SdkTypeId type) {
    return Collections.emptyList();
  }

  @Override
  public void addJdk(Sdk jdk) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeJdk(Sdk jdk) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateJdk(Sdk originalJdk, Sdk modifiedJdk) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(Listener listener) {
  }

  @Override
  public void removeListener(Listener listener) {
  }

  @Override
  public SdkTypeId getDefaultSdkType() {
    return CoreSdkType.INSTANCE;
  }

  @Override
  public SdkTypeId getSdkTypeByName(String name) {
    return CoreSdkType.INSTANCE;
  }

  @Override
  public Sdk createSdk(String name, SdkTypeId sdkType) {
    throw new UnsupportedOperationException();
  }
}
