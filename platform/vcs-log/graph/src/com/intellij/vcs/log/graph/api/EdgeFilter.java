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
package com.intellij.vcs.log.graph.api;

public enum EdgeFilter {
  ALL(true, true, true),
  NORMAL_DOWN(false, true, false),
  NORMAL_UP(true, false, false),
  NORMAL_ALL(true, true, false),
  SPECIAL(false, false, true);

  public final boolean upNormal;
  public final boolean downNormal;
  public final boolean special;

  EdgeFilter(boolean upNormal, boolean downNormal, boolean special) {
    this.upNormal = upNormal;
    this.downNormal = downNormal;
    this.special = special;
  }
}
