/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.containers;

import java.util.Map;

@SuppressWarnings("ClassNameSameAsAncestorName")
public class HashMap<K, V> extends java.util.HashMap<K, V> {
  public HashMap() { }

  public HashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public HashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public <K1 extends K, V1 extends V> HashMap(Map<? extends K1, ? extends V1> map) {
    super(map);
  }

  @Override
  public void clear() {
    if (size() == 0) return; // optimization
    super.clear();
  }
}
