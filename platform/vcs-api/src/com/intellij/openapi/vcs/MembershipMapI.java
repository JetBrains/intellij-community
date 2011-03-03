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

import com.intellij.openapi.util.Pair;
import com.intellij.util.PairProcessor;

/**
 * @author irengrig
 *         Date: 2/11/11
 *         Time: 7:24 PM
 */
public interface MembershipMapI<Key, Val> extends AreaMapI<Key, Val>{
  void putOptimal(Key key, Val val);
  void optimizeMap(PairProcessor<Val, Val> valuesAreas);
  Pair<Key, Val> getMapping(Key key);
}
