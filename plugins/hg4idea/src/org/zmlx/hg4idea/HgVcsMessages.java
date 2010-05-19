// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import com.intellij.CommonBundle;

public final class HgVcsMessages {

  private static Reference<ResourceBundle> ourBundle;

  private static final String BUNDLE = "org.zmlx.hg4idea.HgVcsMessages";

  private HgVcsMessages() { }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) {
      bundle = ourBundle.get();
    }
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }

}
