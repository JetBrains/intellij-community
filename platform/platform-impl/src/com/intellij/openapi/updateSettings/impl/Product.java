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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Product {
  private final String myName;
  private final Set<String> myCodes;
  private final List<UpdateChannel> myChannels;

  public Product(Element node) {
    myName = node.getAttributeValue("name");
    myCodes = new LinkedHashSet<String>();
    myChannels = new ArrayList<UpdateChannel>();

    List codes = node.getChildren("code");
    for (Object code : codes) {
      myCodes.add(((Element)code).getValue());
    }

    List channels = node.getChildren("channel");
    for (Object channel : channels) {
      myChannels.add(new UpdateChannel((Element)channel));
    }
  }

  public boolean hasCode(String code) {
    return myCodes.contains(code);
  }

  @Nullable
  public UpdateChannel findUpdateChannelById(String id) {
    for (UpdateChannel channel : myChannels) {
      if (id.equals(channel.getId())) return channel;
    }
    return null;
  }

  @NotNull
  public List<UpdateChannel> getChannels() {
    return myChannels;
  }

  public String getName() {
    return myName;
  }

  public List<String> getAllChannelIds() {
    List<String> result = new ArrayList<String>();
    for (UpdateChannel channel : myChannels) {
      result.add(channel.getId());
    }
    return result;
  }
}
