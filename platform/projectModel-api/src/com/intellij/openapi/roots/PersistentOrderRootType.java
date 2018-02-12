/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots;

import org.jetbrains.annotations.NonNls;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PersistentOrderRootType extends OrderRootType {
  private final String mySdkRootName;
  private final String myModulePathsName;
  private final String myOldSdkRootName;

  protected PersistentOrderRootType(@NonNls String name, @NonNls @Nullable String sdkRootName, @NonNls @Nullable String modulePathsName, @Nullable @NonNls final String oldSdkRootName) {
    super(name);
    mySdkRootName = sdkRootName;
    myModulePathsName = modulePathsName;
    myOldSdkRootName = oldSdkRootName;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourPersistentOrderRootTypes = ArrayUtil.append(ourPersistentOrderRootTypes, this);
  }

  /**
   * @return Element name used for storing roots of this type in JDK definitions.
   */
  @Nullable
  public String getSdkRootName() {
    return mySdkRootName;
  }

  @Nullable
  public String getOldSdkRootName() {
    return myOldSdkRootName;
  }

  /**
   * @return Element name used for storing roots of this type in module definitions.
   */
  @Nullable
  public String getModulePathsName() {
    return myModulePathsName;
  }
}
