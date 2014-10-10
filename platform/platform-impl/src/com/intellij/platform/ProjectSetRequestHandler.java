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

import com.intellij.openapi.application.ApplicationManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.Responses;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetRequestHandler extends HttpRequestHandler {

  @Override
  public boolean isSupported(@NotNull FullHttpRequest request) {
    return request.method() == HttpMethod.POST && "/openProjectSet".equals(request.uri());
  }

  @Override
  public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context)
    throws IOException {

    @Language("JSON") final String desc = request.content().toString(Charset.defaultCharset());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new ProjectSetReader().readDescriptor(desc, null);
      }
    });
    Responses.sendStatus(HttpResponseStatus.OK, context.channel(), request);
    return true;
  }
}
