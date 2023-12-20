// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

/**
 * The `SdkAdditionalData` interface represents additional data associated with an SDK.
 * This interface should be implemented by classes that need to provide additional data for an SDK.
 * <p>
 * It's possible to create an object from scratch or update it via [SdkModificator]. Changing already
 * atached to the SDK object outside of the [SdkModificator] is prohibited to catch all plases where
 * you have mutations outside the modificator, implement [markAsCommited] or use [SdkAdditionalDataBase]
 * as a base class for your class and add [SdkAdditionalDataBase#assertWritable()] to all your setter
 * methods.
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
