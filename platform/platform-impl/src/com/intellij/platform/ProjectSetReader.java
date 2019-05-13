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
package com.intellij.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetReader {
  public void readDescriptor(@NotNull JsonObject descriptor, @Nullable ProjectSetProcessor.Context context) {
    Map<String, ProjectSetProcessor> processors = new HashMap<>();
    for (ProjectSetProcessor extension : ProjectSetProcessor.EXTENSION_POINT_NAME.getExtensions()) {
      processors.put(extension.getId(), extension);
    }

    if (context == null) {
      context = new ProjectSetProcessor.Context();
    }
    context.directoryName = "";
    if (descriptor.get(ProjectSetProcessor.PROJECT) == null) {
      descriptor.add(ProjectSetProcessor.PROJECT, new JsonObject()); // open directory by default
    }
    runProcessor(processors, context, descriptor.entrySet().iterator());
  }

  private static void runProcessor(final Map<String, ProjectSetProcessor> processors, final ProjectSetProcessor.Context context, final Iterator<Map.Entry<String, JsonElement>> iterator) {
    if (!iterator.hasNext()) return;
    final Map.Entry<String, JsonElement> entry = iterator.next();
    String key = entry.getKey();
    ProjectSetProcessor processor = processors.get(key);
    if (processor == null) {
      LOG.error("Processor not found for " + key);
      return;
    }

    List<Pair<String, String>> list;
    if (entry.getValue().isJsonObject()) {
      JsonObject object = entry.getValue().getAsJsonObject();
      if (object.entrySet().size() == 1 && object.entrySet().iterator().next().getValue().isJsonArray()) {
        final Map.Entry<String, JsonElement> next = object.entrySet().iterator().next();
        list = ContainerUtil.map(next.getValue().getAsJsonArray(),
                                 o -> Pair.create(next.getKey(), getString(o)));
      }
      else {
        list = ContainerUtil.map(object.entrySet(), entry1 -> {
          JsonElement value = entry1.getValue();
          return Pair.create(entry1.getKey(), getString(value));
        });
      }
    }
    else {
      list = Collections.singletonList(Pair.create(entry.getKey(), entry.getValue().getAsString()));
    }
    processor.processEntries(list, context, () -> runProcessor(processors, context, iterator));
  }

  public static String getString(JsonElement value) {
    return value instanceof JsonPrimitive ? value.getAsString() : value.toString();
  }

  private static final Logger LOG = Logger.getInstance(ProjectSetReader.class);
}
