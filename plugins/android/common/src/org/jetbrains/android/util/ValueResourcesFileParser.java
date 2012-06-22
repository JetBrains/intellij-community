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
package org.jetbrains.android.util;

import com.android.resources.ResourceType;
import com.intellij.util.containers.Stack;
import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;

/**
* @author Eugene.Kudelevsky
*/
public abstract class ValueResourcesFileParser implements IXMLBuilder {
  private boolean mySeenResources;
  private String myLastTypeAttr;
  private String myLastNameAttr;
  private final Stack<String> myContextNames = new Stack<String>();

  public ValueResourcesFileParser() {
    mySeenResources = false;
    myLastTypeAttr = null;
    myLastNameAttr = null;
  }

  @Override
  public void startBuilding(String systemID, int lineNr) throws Exception {
  }

  @Override
  public void newProcessingInstruction(String target, Reader reader) throws Exception {
  }

  @Override
  public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
    if (!mySeenResources) {
      if ("resources".equals(name)) {
        mySeenResources = true;
      }
      else {
        stop();
      }
    }
    myLastNameAttr = null;
    myLastTypeAttr = null;
  }

  protected abstract void stop();

  protected abstract void process(@NotNull ResourceEntry resourceEntry);

  @Override
  public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
    if ("name".equals(key)) {
      myLastNameAttr = value;
    }
    else if ("type".equals(key)) {
      myLastTypeAttr = value;
    }
  }

  @Override
  public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
    if (myLastNameAttr != null && name != null) {
      final String resTypeStr = "item".equals(name)
                             ? myLastTypeAttr
                             : AndroidCommonUtils.getResourceTypeByTagName(name);
      final ResourceType resType = resTypeStr != null ? ResourceType.getEnum(resTypeStr) : null;
      if (resType != null) {
        if (resType == ResourceType.ATTR) {
          final String contextName = myContextNames.peek();
          process(new ResourceEntry(resTypeStr, myLastNameAttr, contextName));
        }
        else {
          process(new ResourceEntry(resTypeStr, myLastNameAttr, ""));
        }
      }
    }
    myContextNames.push(myLastNameAttr != null ? myLastNameAttr : "");
  }

  @Override
  public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
    myContextNames.pop();
  }

  @Override
  public void addPCData(Reader reader, String systemID, int lineNr) throws Exception {
  }

  @Nullable
  @Override
  public Object getResult() throws Exception {
    return null;
  }
}
