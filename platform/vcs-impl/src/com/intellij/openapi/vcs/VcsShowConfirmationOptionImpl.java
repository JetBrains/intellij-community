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
package com.intellij.openapi.vcs;


public class VcsShowConfirmationOptionImpl extends VcsAbstractSetting implements VcsShowConfirmationOption{
  private Value myValue = Value.SHOW_CONFIRMATION;

  private final String myCaption;

  private final String myDoNothingCaption;
  private final String myShowConfirmationCaption;
  private final String myDoActionSilentlyCaption;

  public VcsShowConfirmationOptionImpl(final String displayName,
                                       final String caption,
                                       final String doNothingCaption,
                                       final String showConfirmationCaption,
                                       final String doActionSilentlyCaption) {
    super(displayName);
    myCaption = caption;
    myDoNothingCaption = doNothingCaption;
    myShowConfirmationCaption = showConfirmationCaption;
    myDoActionSilentlyCaption = doActionSilentlyCaption;
  }

  public Value getValue() {
    return myValue;
  }

  public void setValue(Value value) {
    myValue = value;
  }

  @Override
  public boolean isPersistent() {
    return true;
  }
}
