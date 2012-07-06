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

/*
 * @author max
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class ChannelStatus implements Comparable<ChannelStatus> {
  @NonNls public static final String EAP_CODE = "eap";
  @NonNls public static final String RELEASE_CODE = "release";

  public static final ChannelStatus EAP = new ChannelStatus(0, EAP_CODE, "Early Access Program");
  public static final ChannelStatus MILESTONE = new ChannelStatus(1, "milestone", "Milestone Releases");
  public static final ChannelStatus BETA = new ChannelStatus(2, "beta", "Beta Releases or Public Previews");
  public static final ChannelStatus RELEASE = new ChannelStatus(3, RELEASE_CODE, "New Major Version Releases");

  private static final List<ChannelStatus> ALL_TYPES = ContainerUtil.immutableList(RELEASE, BETA, MILESTONE, EAP);

  private final int myOrder;
  private final String myCode;
  private final String myDisplayName;

  private ChannelStatus(int order, String code, String displayName) {
    myOrder = order;
    myCode = code;
    myDisplayName = displayName;
  }

  public static ChannelStatus fromCode(String code) {
    if (EAP_CODE.equalsIgnoreCase(code)) return EAP;
    if ("milestone".equalsIgnoreCase(code)) return MILESTONE;
    if ("beta".equalsIgnoreCase(code)) return BETA;

    return RELEASE;
  }

  public int compareTo(ChannelStatus o) {
    return myOrder - o.myOrder;
  }

  public String getCode() {
    return myCode;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public static List<ChannelStatus> all() {
    return ALL_TYPES;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }
}
