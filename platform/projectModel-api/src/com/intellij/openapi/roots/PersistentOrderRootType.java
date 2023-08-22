// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PersistentOrderRootType extends OrderRootType {
  private final String mySdkRootName;
  private final String myModulePathsName;
  private final String myOldSdkRootName;

  protected PersistentOrderRootType(@NonNls @NotNull String name, @NonNls @Nullable String sdkRootName, @NonNls @Nullable String modulePathsName, @Nullable @NonNls final String oldSdkRootName) {
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
