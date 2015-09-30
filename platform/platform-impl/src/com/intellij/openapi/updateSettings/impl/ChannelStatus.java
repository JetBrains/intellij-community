/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

/**
 * @author max
 */
public enum ChannelStatus {
  EAP("eap", "Early Access Program"),
  MILESTONE("milestone", "Milestone Releases"),
  BETA("beta", "Beta Releases or Public Previews"),
  RELEASE("release", "New Major Version Releases");

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
