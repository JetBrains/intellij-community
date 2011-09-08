/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.legacy.LegacyCallback;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class ProjectCallback extends LegacyCallback implements IProjectCallback {

  private final Map<ResourceType, Map<String, Integer>> myResource2Id = new HashMap<ResourceType, Map<String, Integer>>();
  private final Map<Integer, Pair<ResourceType, String>> myId2Resource = new HashMap<Integer, Pair<ResourceType, String>>();

  // start resource ids at 0x7f000000 to avoid colliding with the framework ids
  private int myIdCounter = 0x7f000000;

  @Nullable
  public AdapterBinding getAdapterBinding(ResourceReference adapterViewRef, Object adapterCookie, Object viewObject) {
    return null;
  }

  @Nullable
  public Object getAdapterItemValue(ResourceReference adapterView,
                                    Object adapterCookie,
                                    ResourceReference itemRef,
                                    int fullPosition,
                                    int positionPerType,
                                    int fullParentPosition,
                                    int parentPositionPerType,
                                    ResourceReference viewRef,
                                    ViewAttribute viewAttribute,
                                    Object defaultValue) {
    return null;
  }

  @Nullable
  public String getNamespace() {
    return null;
  }

  @Nullable
  public ILayoutPullParser getParser(String layoutName) {
    // don't support custom parser for included files.
    return null;
  }

  @Nullable
  @Override
  public ILayoutPullParser getParser(ResourceValue layoutResource) {
    return null;
  }

  public Integer getResourceId(ResourceType type, String name) {
    Map<String, Integer> typeMap = myResource2Id.get(type);
    if (typeMap == null) {
      typeMap = new HashMap<String, Integer>();
      myResource2Id.put(type, typeMap);
    }

    Integer value = typeMap.get(name);
    if (value == null) {
      value = ++myIdCounter;
      typeMap.put(name, value);
      myId2Resource.put(value, Pair.of(type, name));
    }

    return value;
  }

  @Nullable
  public Object loadView(String name, Class[] constructorSignature, Object[] constructorArgs)
    throws ClassNotFoundException, Exception {
    // todo: support custom views
    return null;
  }

  public Pair<ResourceType, String> resolveResourceId(int id) {
    return myId2Resource.get(id);
  }

  @Nullable
  public String resolveResourceId(int[] id) {
    // this is needed only when custom views have custom styleable
    return null;
  }
}
