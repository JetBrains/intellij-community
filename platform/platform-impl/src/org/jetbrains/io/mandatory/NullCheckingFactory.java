/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.io.mandatory;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.util.ReflectionUtil;

import java.io.IOException;
import java.lang.reflect.Field;

/**
* @author Mikhail Golubev
*/
public class NullCheckingFactory implements TypeAdapterFactory {
  public static final NullCheckingFactory INSTANCE = new NullCheckingFactory();

  @Override
  public <T> TypeAdapter<T> create(Gson gson, final TypeToken<T> type) {
    if (type.getRawType().getAnnotation(RestModel.class) == null) {
      return null;
    }
    final TypeAdapter<T> defaultAdapter = gson.getDelegateAdapter(this, type);
    return new TypeAdapter<T>() {
      @Override
      public void write(JsonWriter out, T value) throws IOException {
        defaultAdapter.write(out, value);
      }

      @Override
      public T read(JsonReader in) throws IOException {
        T stub = defaultAdapter.read(in);
        if (stub == null) return null;

        for (Field field : ReflectionUtil.collectFields(type.getRawType())) {
          if (field.getAnnotation(Mandatory.class) != null) {
            try {
              field.setAccessible(true);
              if (field.get(stub) == null) {
                throw new JsonMandatoryException(String.format("Field '%s' is mandatory, but missing in response", field.getName()));
              }
            }
            catch (IllegalAccessException e) {
              throw new RuntimeException(e);
            }
          }
        }
        return stub;
      }
    };
  }
}
