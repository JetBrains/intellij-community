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

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UpdateChannel {
  private final String myId;
  private final String myName;
  private final ChannelStatus myStatus;
  private final String myLicensing;
  private final int myMajorVersion;
  private final String myHomePageUrl;
  private final String myFeedbackUrl;
  private final List<BuildInfo> myBuilds;
  private final int myEvalDays;

  public static final String LICENSING_EAP = "eap";
  public static final String LICENSING_PRODUCTION = "production";

  public UpdateChannel(Element node) {
    myId = node.getAttributeValue("id");
    myName = node.getAttributeValue("name");
    myStatus = ChannelStatus.fromCode(node.getAttributeValue("status"));
    String licensing = node.getAttributeValue("licensing");
    myLicensing = licensing != null ? licensing : LICENSING_PRODUCTION;

    String majorVersion = node.getAttributeValue("majorVersion");
    myMajorVersion = majorVersion != null ? Integer.parseInt(majorVersion) : -1;

    String evalDays = node.getAttributeValue("evalDays");
    myEvalDays = evalDays != null ? Integer.parseInt(evalDays) : 30;

    myHomePageUrl = node.getAttributeValue("url");
    myFeedbackUrl = node.getAttributeValue("feedback");

    myBuilds = new ArrayList<BuildInfo>();
    for (Object child : node.getChildren("build")) {
      myBuilds.add(new BuildInfo((Element)child));
    }
  }

  @Nullable
  public BuildInfo getLatestBuild() {
    BuildInfo build = null;
    for (BuildInfo info : myBuilds) {
      if (build == null || build.compareTo(info) < 0) {
        build = info;
      }
    }

    return build;
  }

  public int getMajorVersion() {
    return myMajorVersion;
  }

  public String getId() {
    return myId;
  }

  public String getHomePageUrl() {
    return myHomePageUrl;
  }

  public String getFeedbackUrl() {
    return myFeedbackUrl;
  }

  public String getName() {
    return myName;
  }

  public ChannelStatus getStatus() {
    return myStatus;
  }

  public String getLicensing() {
    return myLicensing;
  }

  public int getEvalDays() {
    return myEvalDays;
  }
}
