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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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

  public boolean readDescriptor(@Language("JSON") @NotNull String descriptor, @Nullable VirtualFile forTests) {

    Application application = ApplicationManager.getApplication();
    LOG.assertTrue(application.isUnitTestMode() || !application.isDispatchThread(), "should not be invoked from EDT");

    ProjectSetProcessor[] extensions = ProjectSetProcessor.EXTENSION_POINT_NAME.getExtensions();
    Map<String, ProjectSetProcessor> processors = new HashMap<String, ProjectSetProcessor>();
    for (ProjectSetProcessor extension : extensions) {
      processors.put(extension.getId(), extension);
    }

    JsonElement parse = new JsonParser().parse(descriptor);

    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directory = forTests;
    for (Map.Entry<String, JsonElement> entry : parse.getAsJsonObject().entrySet()) {
      String key = entry.getKey();
      ProjectSetProcessor processor = processors.get(key);
      if (processor == null) {
        LOG.error("Processor not found for " + key);
        return false;
      }

      JsonObject object = entry.getValue().getAsJsonObject();
      List<Pair<String, String>> list =
        ContainerUtil.map(object.entrySet(), new Function<Map.Entry<String, JsonElement>, Pair<String, String>>() {
          @Override
          public Pair<String, String> fun(Map.Entry<String, JsonElement> entry) {
            JsonElement value = entry.getValue();
            return Pair.create(entry.getKey(), value instanceof JsonPrimitive ? value.getAsString() : value.toString());
          }
        });
      if (!processor.processEntries(list, context)) return false;
    }
    return true;
  }

  private static final Logger LOG = Logger.getInstance(ProjectSetReader.class);
}
