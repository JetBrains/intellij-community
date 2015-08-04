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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.SafeWriteRequestor;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SaveSessionBase implements StateStorage.SaveSession, StateStorage.ExternalizationSession, SafeWriteRequestor {
  private SkipDefaultsSerializationFilter serializationFilter;

  @SuppressWarnings("deprecation")
  @Override
  public final void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state, Storage storageSpec) {
    Element element;
    try {
      if (state instanceof Element) {
        element = (Element)state;
      }
      else if (state instanceof JDOMExternalizable) {
        element = new Element("temp_element");
        ((JDOMExternalizable)state).writeExternal(element);
      }
      else {
        if (serializationFilter == null) {
          serializationFilter = new SkipDefaultsSerializationFilter();
        }
        element = XmlSerializer.serializeIfNotDefault(state, serializationFilter);
      }
    }
    catch (WriteExternalException e) {
      StateStorageBase.LOG.debug(e);
      return;
    }
    catch (Throwable e) {
      StateStorageBase.LOG.error("Unable to serialize " + componentName + " state", e);
      return;
    }

    setSerializedState(component, componentName, element);
  }

  protected abstract void setSerializedState(@NotNull Object component, @NotNull String componentName, @Nullable Element element);
}
