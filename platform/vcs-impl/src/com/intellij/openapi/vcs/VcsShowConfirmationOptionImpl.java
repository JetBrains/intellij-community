/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link VcsShowConfirmationOption#STATIC_SHOW_CONFIRMATION}
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public class VcsShowConfirmationOptionImpl implements VcsShowConfirmationOption {
  private Value myValue = Value.SHOW_CONFIRMATION;

  public VcsShowConfirmationOptionImpl(final String displayName,
                                       final String caption,
                                       final String doNothingCaption,
                                       final String showConfirmationCaption,
                                       final String doActionSilentlyCaption) {
  }

  @Override
  public Value getValue() {
    return myValue;
  }

  @Override
  public void setValue(Value value) {
    myValue = value;
  }

  @Override
  public boolean isPersistent() {
    return true;
  }
}
