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

package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author max
 */
public class BuildInfo implements Comparable<BuildInfo> {
  private final BuildNumber myNumber;
  private final String myVersion;
  private final String myMessage;
  private final Date myReleaseDate;
  private final List<PatchInfo> myPatches;
  private final List<ButtonInfo> myButtons;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.BuildInfo");

  public BuildInfo(Element node) {
    myNumber = BuildNumber.fromString(node.getAttributeValue("number"));
    myVersion = node.getAttributeValue("version");

    Date releaseDate = null;
    String date = node.getAttributeValue("date");
    if (date != null) {
      try {
        releaseDate = new SimpleDateFormat("dd.MM.yyyy").parse(date);
      }
      catch (ParseException e) {
        LOG.info("Failed to parse build release date " + date);
      }
    }
    myReleaseDate = releaseDate;

    myPatches = new ArrayList<PatchInfo>();
    for (Object patchNode : node.getChildren("patch")) {
      myPatches.add(new PatchInfo((Element)patchNode));
    }
    
    myButtons = new ArrayList<ButtonInfo>();
    for (Object buttonNode : node.getChildren("button")) {
      myButtons.add(new ButtonInfo((Element) buttonNode));
    }

    Element messageTag = node.getChild("message");
    myMessage = messageTag != null ? messageTag.getValue() : "";
  }

  public int compareTo(BuildInfo o) {
    return myNumber.compareTo(o.myNumber);
  }

  public BuildNumber getNumber() {
    return myNumber;
  }

  public String getVersion() {
    return myVersion != null ? myVersion : "";
  }

  public String getMessage() {
    return myMessage;
  }

  public Date getReleaseDate() {
    return myReleaseDate;
  }

  @Nullable
  public PatchInfo findPatchForCurrentBuild() {
    BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    for (PatchInfo each : myPatches) {
      if (each.isAvailable() && each.getFromBuild().asStringWithoutProductCode().equals(currentBuild.asStringWithoutProductCode())) 
        return each;
    }
    return null;
  }

  public List<ButtonInfo> getButtons() {
    return ContainerUtil.immutableList(myButtons);
  }

  @Override
  public String toString() {
    return "BuildInfo(number=" + myNumber + ")";
  }
}
