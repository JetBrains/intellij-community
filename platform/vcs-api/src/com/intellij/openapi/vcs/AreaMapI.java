/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 2/11/11
 *         Time: 7:34 PM
 */
public interface AreaMapI<Key, Val> {
  void putAll(AreaMapI<Key, Val> other);
  void put(Key key, Val val);
  Collection<Val> values();
  Collection<Key> keySet();
  @Nullable
  Val getExact(Key key);
  void remove(Key key);
  boolean contains(Key key);
  void clear();
}
