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
package org.jetbrains.ide;

import com.google.gson.stream.JsonReader;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class DiffHttpService extends RestService {
  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.POST;
  }

  @NotNull
  @Override
  protected String getServiceName() {
    return "diff";
  }

  @Override
  @Nullable
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    final List<DiffContent> contents = new ArrayList<DiffContent>();
    final List<String> titles = new ArrayList<String>();

    JsonReader reader = createJsonReader(request);
    if (reader.hasNext()) {
      String fileType = null;
      boolean reformat;
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("fileTypeName")) {
          fileType = reader.nextString();
        }
        else if (name.equals("contents")) {
          String error = readContent(reader, contents, titles, fileType);
          if (error != null) {
            return error;
          }
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    if (contents.isEmpty()) {
      return "Empty request";
    }

    final Project project = guessProject();
    if (project == null) {
      // Argument for @NotNull parameter 'project' of com/intellij/openapi/components/ServiceManager.getService must not be null
      return "No opened project, please open any project";
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        DiffManager.getInstance().getDiffTool().show(new DiffRequest(project) {
          @NotNull
          @Override
          public DiffContent[] getContents() {
            return contents.toArray(new DiffContent[contents.size()]);
          }

          @Override
          public String[] getContentTitles() {
            return ArrayUtil.toStringArray(titles);
          }

          @Override
          public String getWindowTitle() {
            return "Diff Service";
          }
        });

        ProjectUtil.focusProjectWindow(project, true);
      }
    }, project.getDisposed());
    return null;
  }

  @Nullable
  private static String readContent(@NotNull JsonReader reader, @NotNull List<DiffContent> contents, @NotNull List<String> titles, @Nullable String defaultFileTypeName) throws IOException {
    FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();

    FileType defaultFileType = defaultFileTypeName == null ? null : fileTypeRegistry.findFileTypeByName(defaultFileTypeName);
    reader.beginArray();
    while (reader.hasNext()) {
      String title = null;
      String fileType = null;
      String content = null;

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("title")) {
          title = reader.nextString();
        }
        else if (name.equals("fileTypeName")) {
          fileType = reader.nextString();
        }
        else if (name.equals("content")) {
          content = reader.nextString();
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();

      if (content == null) {;
        return "content is not specified";
      }

      contents.add(new SimpleContent(content, fileType == null ? defaultFileType : fileTypeRegistry.findFileTypeByName(fileType)));
      titles.add(StringUtil.isEmptyOrSpaces(title) ? "" : title);
    }
    reader.endArray();
    return null;
  }
}
