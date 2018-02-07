// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.util.Comparing;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * @author yole
 */
public interface SdkTypeId {
  @NotNull
  String getName();

  @Nullable
  String getVersionString(@NotNull Sdk sdk);

  void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional);

  @Nullable
  SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, Element additional);

  /**
   * An SDK can be located on a local machine or on a remote or virtual machine. In the latter case this method returns false.
   */
  default boolean isLocalSdk(@NotNull Sdk sdk) {
    return true;
  }

  /**
   * A naive lexicographical comparator. Most probably you'll need to override this method.
   */
  default Comparator<Sdk> versionComparator() {
    return (sdk1, sdk2) -> {
      assert sdk1.getSdkType() == this : sdk1;
      assert sdk2.getSdkType() == this : sdk2;
      return Comparing.compare(sdk1.getVersionString(), sdk2.getVersionString());
    };
  }
}