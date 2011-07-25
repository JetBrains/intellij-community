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

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.legacy.ILegacyPullParser;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;

/**
 * @author Eugene.Kudelevsky
 */
class XmlParser extends KXmlParser implements ILayoutPullParser, ILegacyPullParser {

  @Nullable
  public ILayoutPullParser getParser(String layoutName) {
    return null;
  }

  @Nullable
  public Object getViewCookie() {
    return null;
  }

  @Nullable
  @Override
  public Object getViewKey() {
    return null;
  }
}
