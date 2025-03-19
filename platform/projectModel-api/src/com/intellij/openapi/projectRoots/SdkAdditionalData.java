// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

/**
 * Represents additional data associated with an SDK.
 * Should be implemented by classes that need to provide additional data for an {@link Sdk}.
 * <p>
 * It's possible to create an object from scratch or update it via {@link SdkModificator}.
 * Changing an instance already attached to the SDK outside of the {@code SdkModificator} is prohibited
 * to catch all places where state is changed outside the modificator.
 * <p>
 * Implement {@link #markAsCommited()} or use {@link com.intellij.openapi.projectRoots.impl.SdkAdditionalDataBase SdkAdditionalDataBase}
 * as a base class and add {@link com.intellij.openapi.projectRoots.impl.SdkAdditionalDataBase#assertWritable() SdkAdditionalDataBase#assertWritable()}
 * to all setter methods.
 */
public interface SdkAdditionalData {
  /**
   * Invocation of this method indicates that additional data must become read-only and report errors on attempt to make changes.
   * Any changes made after won't be persisted and will be discarded after the next SDK update.
   * <p>>
   * To make proper additional data modification:<ul>
   * <li>Obtain {@link SdkModificator} via {@link Sdk#getSdkModificator()}</li>
   * <li>Obtain additional data from the modificator using {@link SdkModificator#getSdkAdditionalData()} / set new additional data using {@link SdkModificator#setSdkAdditionalData(SdkAdditionalData)}</li>
   * <li>Make any modifications to the additional data</li>
   * <li>Commit modificator using {@link SdkModificator#commitChanges()}</li>
   * </ul>
   *
   * @implSpec any additional data is expected to be writable after creation and becomes read only after invoking this method.
   */
  default void markAsCommited() { }
}
