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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageId;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;


@SuppressWarnings({"deprecation"})
class DefaultStateSerializer {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DefaultStateSerializer");

  private DefaultStateSerializer() {
  }

  static Element serializeState(Object state, final Storage storage) throws  WriteExternalException {
    if (state instanceof Element) {
      return (Element)state;
    }
    else if (state instanceof JDOMExternalizable) {
      JDOMExternalizable jdomExternalizable = (JDOMExternalizable)state;

      final Element element = new Element("temp_element");
      try {
        jdomExternalizable.writeExternal(element);
      }
      catch (WriteExternalException e) {
        throw e;
      }catch (Throwable e) {
        LOG.info("Unable to serialize component state!", e);
        return new Element("empty");
      }
      return element;
    }
    else {
      return  XmlSerializer.serialize(state, new SkipDefaultValuesSerializationFilters() {
        @Override
        public boolean accepts(final Accessor accessor, final Object bean) {
          if (!super.accepts(accessor, bean)) return false;

          if (storage != null) {
            final Annotation[] annotations = accessor.getAnnotations();
            for (Annotation annotation : annotations) {
              if (StorageId.class.isAssignableFrom(annotation.annotationType())) {
                StorageId storageId = (StorageId)annotation;

                if (!storageId.value().equals(storage.id())) return false;
              }
            }

            return storage.isDefault();
          }

          return true;
        }
      });
    }
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  static <T> T deserializeState(@Nullable Element stateElement, Class <T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    if (stateElement == null) return mergeInto;

    if (stateClass.equals(Element.class)) {
      //assert mergeInto == null;
      return (T)stateElement;
    }
    else if (JDOMExternalizable.class.isAssignableFrom(stateClass)) {
      if (mergeInto != null) {
        String elementText = JDOMUtil.writeElement(stateElement, "\n");
        LOG.error("State is " + stateClass.getName() + ", merge into is " + mergeInto.toString() + ", state element text is " + elementText);
      }
      try {
        final T t = stateClass.newInstance();
        try {
          ((JDOMExternalizable)t).readExternal(stateElement);
          return t;
        }
        catch (InvalidDataException e) {
          throw new StateStorageException(e);
        }
      }
      catch (InstantiationException e) {
        throw new StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorageException(e);
      }
    }
    else {
      if (mergeInto == null) {
        return XmlSerializer.deserialize(stateElement, stateClass);
      }
      else {
        XmlSerializer.deserializeInto(mergeInto, stateElement);
        return mergeInto;
      }
    }
  }

}
