// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;


public interface SdkTypeId {
  @NotNull
  String getName();

  @Nullable
  String getVersionString(@NotNull Sdk sdk);

  void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional);

  @Nullable
  SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, @NotNull Element additional);

  /**
   * An SDK can be located on a local machine or on a remote or virtual machine. In the latter case this method returns false.
   */
  default boolean isLocalSdk(@NotNull Sdk sdk) {
    return true;
  }

  /**
   * Note to implementors: you may need to override this method if SDKs of this type have non-trivial version strings.
   */
  default @NotNull Comparator<Sdk> versionComparator() {
    Comparator<String> versionStringComparator = versionStringComparator();
    return (sdk1, sdk2) -> {
      assert sdk1.getSdkType() == this : sdk1;
      assert sdk2.getSdkType() == this : sdk2;
      return versionStringComparator.compare(sdk1.getVersionString(), sdk2.getVersionString());
    };
  }

  /**
   * A comparator to compare versions of SDKs of that SdkType, e.g. versions from
   * {@link Sdk#getVersionString()} or {@link SdkType#getVersionString}
   * <br />
   * The implementation has to be synchronized with {@link #versionComparator()}
   */
  default @NotNull Comparator<String> versionStringComparator() {
    return (v1, v2) -> StringUtil.compareVersionNumbers(v1, v2);
  }

 default boolean allowWslSdkForLocalProject() {
    return false;
 }
}
