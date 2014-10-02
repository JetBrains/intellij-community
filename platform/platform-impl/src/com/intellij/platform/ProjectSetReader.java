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
package com.intellij.platform;

import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetReader {

  public void readDescriptor(@Language("JSON") @NotNull String descriptor, @Nullable VirtualFile forTests) {

    ProjectSetProcessor[] extensions = ProjectSetProcessor.EXTENSION_POINT_NAME.getExtensions();
    Map<String, ProjectSetProcessor> processors = new HashMap<String, ProjectSetProcessor>();
    for (ProjectSetProcessor extension : extensions) {
      processors.put(extension.getId(), extension);
    }

    JsonElement parse = null;
    try {
      parse = new JsonParser().parse(descriptor);
    }
    catch (JsonSyntaxException e) {
      LOG.error(e);
      return;
    }
    Iterator<Map.Entry<String, JsonElement>> iterator = parse.getAsJsonObject().entrySet().iterator();
    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directory = forTests;
    runProcessor(processors, context, iterator);
  }

  private static void runProcessor(final Map<String, ProjectSetProcessor> processors, final ProjectSetProcessor.Context context, final Iterator<Map.Entry<String, JsonElement>> iterator) {
    if (!iterator.hasNext()) return;
    Map.Entry<String, JsonElement> entry = iterator.next();
    String key = entry.getKey();
    ProjectSetProcessor processor = processors.get(key);
    if (processor == null) {
      LOG.error("Processor not found for " + key);
      return;
    }

    List<Pair<String, String>> list;
    if (entry.getValue().isJsonObject()) {
      JsonObject object = entry.getValue().getAsJsonObject();
      list = ContainerUtil.map(object.entrySet(), new Function<Map.Entry<String, JsonElement>, Pair<String, String>>() {
        @Override
        public Pair<String, String> fun(Map.Entry<String, JsonElement> entry) {
          JsonElement value = entry.getValue();
          return Pair.create(entry.getKey(), value instanceof JsonPrimitive ? value.getAsString() : value.toString());
        }
      });
    }
    else {
      list = Collections.singletonList(Pair.create(entry.getKey(), entry.getValue().getAsString()));
    }
    processor.processEntries(list, context, new Runnable() {
      @Override
      public void run() {
        runProcessor(processors, context, iterator);
      }
    });
  }

  private static final Logger LOG = Logger.getInstance(ProjectSetReader.class);
}
