// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

public enum ChannelStatus {
  EAP("eap", "Early Access Program"),
  MILESTONE("milestone", "Milestone EAP Builds"),
  BETA("beta", "Beta Releases or Public Previews"),
  RELEASE("release", "Stable Releases");

  private final String myCode;
  private final String myDisplayName;

  ChannelStatus(String code, String displayName) {
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