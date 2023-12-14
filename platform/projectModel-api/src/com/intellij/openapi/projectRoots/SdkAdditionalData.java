// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

/**
 * Container for the SDK properties specific to a particular SDK type.
 */
public interface SdkAdditionalData {
  /**
   * Invocation of this method indicates that additional data must become read-only and report errors on attempt to make changes. Any changes
   * made after won't be persisted and going to be discarded after next sdk update.
   * To make proper additional data modification, you should:<ul>
   * <li>Obtain sdk modificator using {@link Sdk#getSdkModificator()}</li>
   * <li>Obtain additional data from the modificator using {@link SdkModificator#getSdkAdditionalData()} / set new additional data using {@link SdkModificator#setSdkAdditionalData(SdkAdditionalData)}</li>
   * <li>Make any modifications to the additional data</li>
   * <li>Commit modificator using {@link SdkModificator#commitChanges()}</li>
   * </ul>
   *
   * @implSpec any additional data is expected to be writable after cration and become read only after invoking this method.
   */
  default void markAsCommited() { }
}
