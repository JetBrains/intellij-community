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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 06.12.2007
*/
public abstract class IdeaPluginImpl implements IdeaPlugin {
  public String getPluginId() {
    final XmlTag tag = getXmlTag();
    if (tag == null) {
      return null;
    }

    final XmlTag idTag = tag.findFirstSubTag("id");
    if (idTag != null) {
      return idTag.getValue().getTrimmedText();
    }

    final XmlTag name = tag.findFirstSubTag("name");
    return name != null ? name.getValue().getTrimmedText() : null;
  }
}
