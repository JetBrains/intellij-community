// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

public enum ChannelStatus {
  EAP("eap", IdeBundle.message("channel.status.eap")),
  MILESTONE("milestone", IdeBundle.message("channel.status.milestone")),
  BETA("beta", IdeBundle.message("channel.status.beta")),
  RELEASE("release", IdeBundle.message("channel.status.stable"));

  private final String myCode;
  private final String myDisplayName;

  ChannelStatus(@NonNls String code, @Nls String displayName) {
    myCode = code;
    myDisplayName = displayName;
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

  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }
}