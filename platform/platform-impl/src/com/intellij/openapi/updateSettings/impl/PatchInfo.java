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

import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;

import java.util.HashSet;
import java.util.Set;

public class PatchInfo {
  private final BuildNumber myFromBuild;
  private final String mySize;

  private final Set<String> myExcludedOSes = new HashSet<String>();

  public PatchInfo(Element node) {
    myFromBuild = BuildNumber.fromString(node.getAttributeValue("from"));
    mySize = node.getAttributeValue("size");
    
    String excluded = node.getAttributeValue("exclusions");
    if (excluded != null) {
      myExcludedOSes.addAll(ContainerUtil.map(StringUtil.split(excluded, ","), new Function<String, String>() {
        @Override
        public String fun(String s) {
          return s.trim();
        }
      }));
    }
  }

  public BuildNumber getFromBuild() {
    return myFromBuild;
  }

  public String getSize() {
    return mySize;
  }

  public boolean isAvailable() {
    return !myExcludedOSes.contains(getOSSuffix());
  }

  public String getOSSuffix() {
    if (SystemInfo.isWindows) return "win";
    if (SystemInfo.isMac) return "mac";
    if (SystemInfo.isUnix) return "unix";
    return "unknown";
  }
}
