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
package com.intellij.openapi.project.impl;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.net.URLDecoder;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class JBProtocolOpenProjectCommand extends JBProtocolCommand {
  public JBProtocolOpenProjectCommand() {
    super("open");
  }

  @Override
  public void perform(String target, Map<String, String> parameters) {
    String path = URLDecoder.decode(target);
    path = StringUtil.trimStart(path, LocalFileSystem.PROTOCOL_PREFIX);
    ProjectUtil.openProject(path, null, true);
  }
}
