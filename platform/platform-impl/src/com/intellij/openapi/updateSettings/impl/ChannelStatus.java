// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.PropertyKey;

public enum ChannelStatus {
  EAP("eap", "channel.status.eap"),
  MILESTONE("milestone", "channel.status.milestone"),
  BETA("beta", "channel.status.beta"),
  RELEASE("release", "channel.status.stable");

  private final String myCode;
  private final String myDisplayNameKey;

  ChannelStatus(String code, @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String displayNameKey) {
    myCode = code;
    myDisplayNameKey = displayNameKey;
  }

  public static ChannelStatus fromCode(String code) {
    for (ChannelStatus type : values()) {
      if (type.getCode().equalsIgnoreCase(code)) {
        return type;
      }
    }
    return RELEASE;
  }

  public String getCode() {
    return myCode;
  }

  public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return IdeBundle.message(myDisplayNameKey);
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
