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
import com.intellij.projectImport.ProjectSetProcessor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetReader {

  public void readDescriptor(@Language("JSON") @NotNull String descriptor) {

    ProjectSetProcessor[] extensions = ProjectSetProcessor.EXTENSION_POINT_NAME.getExtensions();
    Map<String, ProjectSetProcessor> processors = new HashMap<String, ProjectSetProcessor>();
    for (ProjectSetProcessor extension : extensions) {
      processors.put(extension.getId(), extension);
    }

    JsonElement parse = new JsonParser().parse(descriptor);
    for (Map.Entry<String, JsonElement> entry : parse.getAsJsonObject().entrySet()) {
      String key = entry.getKey();
      ProjectSetProcessor processor = processors.get(key);
      JsonObject object = entry.getValue().getAsJsonObject();
      for (Map.Entry<String, JsonElement> elementEntry : object.entrySet()) {
        Object o = processor.interactWithUser();
        processor.processEntry(elementEntry.getKey(), elementEntry.getValue().getAsString(), o);
      }
    }
  }
}
