/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.preview;

public class PreviewProviderId<V, C> {
  private final String myVisualName;

  public static <V, C> PreviewProviderId<V, C> create(String visualName) {
    return new PreviewProviderId<>(visualName);
  }

  private PreviewProviderId(String visualName) {
    myVisualName = visualName;
  }

  public final String getVisualName() {
    return myVisualName;
  }

  @Override
  public final String toString() {
    return getVisualName();
  }
}
